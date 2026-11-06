"""New idle: becoming absorbed in a book."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
import generate_companion_rig as rig

N=rig.N
FRAMES=216
FRAME_MS=80

def window(t,start,rise,end,fall):
    return rig.smooth((t-start)/rise)*rig.smooth((end-t)/fall)

def gaze(t):
    # Follow two lines, with a pause at the end of each and a smooth return.
    keys=[(0,0),(.06,-5),(.23,5),(.29,5),(.34,-5),(.52,5),(.59,5),(.65,-5),(.80,3),(.86,3),(1,0)]
    for (a,x),(b,y) in zip(keys,keys[1:]):
        if t<=b: return x+(y-x)*rig.smooth((t-a)/(b-a))
    return 0

def make_frame(t):
    if t>=1:t=0
    focus=window(t,.15,.20,.88,.20)
    blink=.30<=t<.31 or .76<=t<.77
    root=ET.fromstring(rig.svg_for('new',['head_group','left_arm','right_arm','book'],
        phase=0,idle_phase=0,action='shared_reading',view_box='92 87 320 320',blinking=blink))
    head=root[1]
    head.set('transform',f'translate(0 {20+4*focus:.3f}) rotate({7+2*focus+0.45*gaze(t)*focus:.3f} 252 270)')
    eyes=head[1][0]
    eyes.set('transform',eyes.get('transform','')+f' translate({gaze(t):.3f} {1.5*focus:.3f})')
    # Book and hands move together, then settle back to the resting pose.
    group=ET.Element(N+'g',{'transform':f'translate(0 {-2.5*focus:.3f})'})
    for n in list(root)[2:]: root.remove(n);group.append(n)
    root.append(group)
    return ET.tostring(root,encoding='unicode')

def main():
    out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
    for i in range(FRAMES): (out/f'{i:03}.svg').write_text(make_frame(i/(FRAMES-1)),encoding='utf8')
if __name__=='__main__':main()
