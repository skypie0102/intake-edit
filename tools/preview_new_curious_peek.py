"""New idle: a quiet, curious peek over the book."""
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
import generate_companion_rig as rig

N=rig.N
FRAMES=192
FRAME_MS=80

def window(t,start,rise,end,fall):
    return rig.smooth((t-start)/rise)*rig.smooth((end-t)/fall)

def make_frame(t):
    if t>=1: t=0
    reading=window(t,.015,.08,.30,.12)+window(t,.82,.08,1,.08)
    peek=window(t,.25,.18,.80,.18)
    tilt=window(t,.40,.14,.70,.14)
    blink=.585<=t<.595 or .82<=t<.83
    root=ET.fromstring(rig.svg_for('new',['head_group','left_arm','right_arm','book'],
        phase=0,idle_phase=0,action='shared_reading',view_box='92 87 320 320',blinking=blink))
    head=root[1]
    head.set('transform',f'translate(0 {20-13*peek:.3f}) rotate({7-10*tilt:.3f} 252 270)')
    eyes=head[1][0]
    scan=3.5*math.sin(2*math.pi*t*2)*reading
    eyes.set('transform',eyes.get('transform','')+f' translate({scan:.3f} {-5*peek:.3f})')
    # The held book does not move, and the head/monocle share a single transform.
    return ET.tostring(root,encoding='unicode')

def main():
    out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
    for i in range(FRAMES): (out/f'{i:03}.svg').write_text(make_frame(i/(FRAMES-1)),encoding='utf8')
if __name__=='__main__': main()
