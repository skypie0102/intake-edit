"""Sad idle: lose focus, look for someone, then retreat into the book."""
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
import generate_companion_rig as rig

N = rig.N
FRAMES = 200
FRAME_MS = 80

def window(t,start,rise,end,fall):
    return rig.smooth((t-start)/rise)*rig.smooth((end-t)/fall)

def make_frame(t):
    if t >= 1: t=0
    read = window(t,.015,.07,.24,.12)
    glance = window(t,.29,.13,.62,.14)
    retreat = window(t,.60,.15,.91,.14)
    blink = .285 <= t < .295 or .625 <= t < .635 or .91 <= t < .92
    root=ET.fromstring(rig.svg_for('sad',['head_group','left_arm','right_arm','book'],
        phase=0,idle_phase=0,action='shared_reading',view_box='92 87 320 320',blinking=blink))
    head=root[1]
    # Lifting his gaze is deliberate; then his head lowers behind the book.
    breath=math.sin((t-.68)*math.pi*5)*retreat
    head.set('transform',f'translate(0 {-8*glance+7*retreat-0.8*breath:.3f}) rotate({-3*glance+3*retreat:.3f} 252 275)')
    eyes=head[1][0]
    scan=(rig.smooth((t-.035)/.13)*5-2.5)*read
    eyes.set('transform',eyes.get('transform','')+f' translate({scan+2*glance:.3f} {3*read-2*glance+3*retreat:.3f})')
    # Hands and book move together: draw the book gently closer before settling.
    book=ET.Element(N+'g',{'transform':f'translate(0 {-8*retreat+0.5*breath:.3f})'})
    for node in list(root)[2:]:
        root.remove(node);book.append(node)
    # Ribbon lags behind the book's approach and settles with it.
    book[2].set('transform',f'rotate({5*math.sin((t-.60)*math.pi*7)*retreat:.3f} 313 341)')
    root.append(book)
    return ET.tostring(root,encoding='unicode')

def main():
    out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
    for i in range(FRAMES):
        (out/f'{i:03}.svg').write_text(make_frame(i/(FRAMES-1)),encoding='utf8')
if __name__ == '__main__': main()
