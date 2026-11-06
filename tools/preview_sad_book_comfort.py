"""Sad idle: hold the book close for a quiet moment of comfort."""
import math
import sys
import xml.etree.ElementTree as ET
from copy import deepcopy
from pathlib import Path
import generate_companion_rig as rig

N = rig.N
FRAMES = 216
FRAME_MS = 80

def window(t,start,rise,end,fall):
    return rig.smooth((t-start)/rise)*rig.smooth((end-t)/fall)

def make_frame(t):
    if t >= 1: t = 0
    reading=window(t,.01,.07,.24,.10)
    hug=window(t,.18,.20,.86,.20)
    rest=window(t,.31,.15,.78,.16)
    closed=window(t,.32,.035,.77,.045)
    breath=math.sin((t-.36)*math.pi*4)*rest
    root=ET.fromstring(rig.svg_for('sad',['head_group','left_arm','right_arm','book'],
        phase=0,idle_phase=0,action='shared_reading',view_box='92 87 320 320'))
    head=root[1]
    head.set('transform',f'translate(0 {12*rest-1.0*breath:.3f}) rotate({-7*rest:.3f} 252 275)')
    eyes=head[1]
    eyes[0].set('transform',eyes[0].get('transform','')+f' translate({2.5*math.sin(t*math.pi*8)*reading:.3f} {3*reading:.3f})')
    eyes.set('opacity',f'{1-closed:.3f}')
    _,_,layers=rig.parts('sad')
    lids=deepcopy(layers['blink']);lids.set('opacity',f'{closed:.3f}');head.append(lids)
    # Draw the book and both hands closer as a single object, with a slow breath.
    book=ET.Element(N+'g',{'transform':f'translate(0 {-11*hug+0.6*breath:.3f}) translate(252 325) scale({1+.025*hug:.5f}) translate(-252 -325)'})
    for n in list(root)[2:]: root.remove(n);book.append(n)
    book[2].set('transform',f'rotate({4*math.sin((t-.18)*math.pi*5)*hug:.3f} 313 341)')
    root.append(book)
    return ET.tostring(root,encoding='unicode')

def main():
    out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
    for i in range(FRAMES): (out/f'{i:03}.svg').write_text(make_frame(i/(FRAMES-1)),encoding='utf8')
if __name__=='__main__': main()
