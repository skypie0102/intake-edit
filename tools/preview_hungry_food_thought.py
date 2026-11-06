"""Hungry idle: a daydream of roasted meat interrupts his reading."""
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
import generate_companion_rig as rig
N=rig.N
FRAMES=196
FRAME_MS=80

def window(t,start,rise,end,fall):
    return rig.smooth((t-start)/rise)*rig.smooth((end-t)/fall)

def make_frame(t):
    if t >= 1: t=0
    reading=window(t,.01,.08,.28,.13)
    dreaming=window(t,.18,.15,.72,.16)
    slump=window(t,.64,.12,.95,.14)
    blink=(.155<=t<.165 or .75<=t<.76)
    root=ET.fromstring(rig.svg_for('hungry',['head_group','left_arm','right_arm','book'],
        phase=0,idle_phase=0,view_box='92 87 320 320',blinking=blink))
    head=root[1]
    head.set('transform',f'translate(0 {3*reading-3*dreaming+5*slump:.3f}) rotate({4*dreaming-3*slump:.3f} 252 270)')
    eyes=head[1][0]
    eyes.set('transform',eyes.get('transform','')+f' translate({-5*dreaming:.3f} {3*reading-4*dreaming+2*slump:.3f})')
    # Let the book and both hands sink together as his attention wanders.
    sink=window(t,.18,.18,.88,.16)
    book=ET.Element(N+'g',{'transform':f'translate(0 {10*sink:.3f})'})
    for node in list(root)[2:]:
        root.remove(node); book.append(node)
    root.append(book)
    # A separate visual daydream: a meat-on-the-bone shape like the 🍖 emoji.
    opacity=window(t,.27,.08,.69,.10)
    if opacity > .001:
        float_y=2*math.sin((t-.27)*math.pi*4)
        g=ET.SubElement(root,N+'g',{'opacity':f'{opacity:.3f}','transform':f'translate(0 {float_y:.2f})'})
        # One bone extends out of both ends of the round roast.
        meat=ET.SubElement(g,N+'g',{'transform':'rotate(-25 141 183)'})
        ET.SubElement(meat,N+'path',{'d':'M116 178 L165 178 C166 173 172 172 174 176 C178 173 183 176 182 180 C185 183 182 188 178 187 C176 191 170 190 169 186 L115 186 C114 190 108 191 106 187 C102 188 99 183 102 180 C101 176 106 173 110 176 C112 172 117 174 116 178 Z','fill':'#fff5dc','stroke':'#926779','stroke-width':'2.8','stroke-linejoin':'round'})
        ET.SubElement(meat,N+'ellipse',{'cx':'142','cy':'182','rx':'21','ry':'16','fill':'#b95d47','stroke':'#754752','stroke-width':'3'})
        ET.SubElement(meat,N+'path',{'d':'M128 176 Q133 169 142 170 M126 183 Q126 189 131 193','fill':'none','stroke':'#f5b78b','stroke-width':'3','stroke-linecap':'round'})
        steam=ET.SubElement(g,N+'g',{'transform':f'translate(0 {-3*rig.smooth((t-.30)/.30):.2f})'})
        ET.SubElement(steam,N+'path',{'d':'M129 163 Q124 158 129 152 M142 159 Q137 153 142 147 M155 163 Q150 158 155 152','fill':'none','stroke':'#e7c5d5','stroke-width':'2.5','stroke-linecap':'round'})
    return ET.tostring(root,encoding='unicode')

def main():
    out=Path(sys.argv[1]); out.mkdir(parents=True,exist_ok=True)
    for i in range(FRAMES): (out/f'{i:03}.svg').write_text(make_frame(i/(FRAMES-1)),encoding='utf8')
if __name__ == '__main__': main()
