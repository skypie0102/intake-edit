"""Mascot v2: experimental original-pixel iris-only gaze layers, NOT a finished rig.

Keeps eyelids/lashes static and translates only inner iris pixels up to +/-2px.
Under-eye sclera is temporary flat underpaint, still requiring proper illustration.
Requires Pillow. The user-approved original portrait's SHA-256 is checked.
"""
from __future__ import annotations
import argparse
import hashlib
import io
import json
from pathlib import Path
from xml.etree import ElementTree as ET
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile
from PIL import Image, ImageChops, ImageDraw
from prepare_mascot_raster import isolate_character

SOURCE_SHA = "57b9a2f264769656eaf39e58e0169cdb41d7c700ea93b535272b4e9efa2c0ce1"
IRIS_POLYGONS = {
    "iris_left": [(204,283),(217,285),(225,291),(223,301),(217,307),(208,305),(204,297)],
    "iris_right": [(288,267),(301,262),(324,258),(327,265),(325,278),(317,286),(302,287),(292,280)],
}


def frame(art):
    x0,y0,x1,y1 = art.getbbox()
    factor = min(604 / (x1-x0), 604 / (y1-y0))
    width,height = round((x1-x0)*factor),round((y1-y0)*factor)
    ox,oy = (640-width)//2,(640-height)//2
    result = Image.new("RGBA",(640,640))
    result.alpha_composite(art.crop((x0,y0,x1,y1)).resize(
        (width,height),Image.Resampling.LANCZOS),(ox,oy))
    def project(points):
        return [(round(ox + (x-x0)*factor), round(oy+(y-y0)*factor)) for x,y in points]
    return result,project


def png_bytes(im):
    buf=io.BytesIO()
    im.save(buf,"PNG")
    return buf.getvalue()


def make_ora(path,reference,background,parts):
    root=ET.Element("image",{"version":"0.0.3","w":"640","h":"640",
                             "name":"Experimental iris-only pilot — NOT production-ready"})
    stack=ET.SubElement(root,"stack")
    ordered=[
        ("RIGHT iris — original pixels",parts["iris_right"],True),
        ("LEFT iris — original pixels",parts["iris_left"],True),
        ("Temporary sclera underpaint — needs artist",background,True),
        ("Original neutral reference — hidden",reference,False),
    ]
    with ZipFile(path,"w") as archive:
        archive.writestr("mimetype","image/openraster",compress_type=ZIP_STORED)
        for i,(name,layer,visible) in enumerate(ordered):
            filename=f"data/layer-{i}.png"
            archive.writestr(filename,png_bytes(layer),compress_type=ZIP_DEFLATED)
            ET.SubElement(stack,"layer",{"name":name,"src":filename,"x":"0","y":"0",
                "opacity":"1.0","visibility":"visible" if visible else "hidden",
                "composite-op":"svg:src-over"})
        archive.writestr("stack.xml",ET.tostring(root,encoding="utf-8",xml_declaration=True))
        archive.writestr("mergedimage.png",png_bytes(reference),compress_type=ZIP_DEFLATED)


def build(portrait,output):
    if hashlib.sha256(portrait.read_bytes()).hexdigest()!=SOURCE_SHA:
        raise ValueError("Use the exact original approved portrait")
    with Image.open(portrait) as original:
        if original.size!=(667,1000) or original.mode!="RGBA":
            raise ValueError("Expected 667x1000 original RGBA portrait")
        reference,project=frame(isolate_character(original))
    output.mkdir(parents=True,exist_ok=True)
    background=reference.copy()
    parts={}
    for name,poly in IRIS_POLYGONS.items():
        polygon=project(poly)
        mask=Image.new("L",reference.size,0)
        ImageDraw.Draw(mask).polygon(polygon,fill=255)
        eye=Image.new("RGBA",reference.size)
        eye.paste(reference,(0,0),mask)
        parts[name]=eye
        # Temporary flat sclera fill; the artist must paint proper underlayers.
        sample_point=(196,292) if name=="iris_left" else (281,286)
        sample=reference.getpixel(project([sample_point])[0])
        ImageDraw.Draw(background).polygon(polygon,fill=sample)
    neutral=background.copy()
    for part in parts.values(): neutral.alpha_composite(part)
    if neutral.tobytes() != reference.tobytes():
        raise AssertionError("Neutral reassembly must be exactly RGBA identical")
    reference.save(output/"neutral-reference.png",optimize=True)
    background.save(output/"temporary-sclera.png",optimize=True)
    neutral.save(output/"neutral-reconstructed.png",optimize=True)
    for name,layer in parts.items():layer.save(output/(name+".png"),optimize=True)
    make_ora(output/"iris-v2.ora",reference,background,parts)
    poses=[]
    for i,delta in enumerate((-2,-1,0,1,2)):
        pose=background.copy()
        for part in parts.values():
            if delta:
                shifted=Image.new("RGBA",part.size)
                shifted.paste(part,(delta,0))
                pose.alpha_composite(shifted)
            else: pose.alpha_composite(part)
        pose.save(output/f"pose-{i}.png",optimize=True)
        poses.append(pose)
    contact=Image.new("RGBA",(1600,300),"#f8f5ee")
    for i,pose in enumerate(poses):
        thumb=pose.crop((160,110,410,240)).resize((320,166),Image.Resampling.LANCZOS)
        contact.alpha_composite(thumb,(i*320,52))
        ImageDraw.Draw(contact).text((i*320+8,15),f"Iris shift {i-2:+d}px",fill="#27312c")
    contact.convert("RGB").save(output/"gaze-contact.png",optimize=True)
    widgets=Image.new("RGBA",(800,160))
    for i,pose in enumerate(poses):
        widgets.alpha_composite(pose.resize((160,160),Image.Resampling.LANCZOS),(i*160,0))
    widgets.save(output/"widget-160-contact.png",optimize=True)
    result={"stage":"phase1b_gaze_test","source_sha256":SOURCE_SHA,"neutral_pixel_exact":True,
            "layers":["iris_left","iris_right"],"eyelashes_fixed":True,"max_shift_px":2,
            "approved":False,"rig_ready":False,"blink_ready":False,
            "limitations":["temporary sclera needs artist repaint",
                           "no independent eyelids or eyebrows","full body remains flat"]}
    (output/"gaze-manifest.json").write_text(json.dumps(result,indent=2)+"\n")
    return result


if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--portrait",type=Path,required=True)
    p.add_argument("--output",type=Path,required=True)
    args=p.parse_args()
    print(json.dumps(build(args.portrait,args.output),indent=2))
