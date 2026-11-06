"""Waiting idle: silently puzzle over a line without the standard thinking pose."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
import generate_companion_rig as rig
N = rig.N
FRAMES = 180
FRAME_MS = 80

def window(t, start, rise, end, fall):
    return rig.smooth((t-start)/rise)*rig.smooth((end-t)/fall)

def make_frame(t):
    if t >= 1: t = 0
    think = window(t, .13, .14, .83, .14)
    turn = rig.smooth((t-.40)/.12)*rig.smooth((.74-t)/.12)
    lower = window(t, .08, .13, .89, .15)
    nod = window(t, .72, .04, .83, .06)
    root = ET.fromstring(rig.svg_for('waiting', ['head_group','left_arm','right_arm','book'],
        phase=0, idle_phase=0, action='shared_reading', view_box='92 87 320 320',
        blinking=(.12 <= t < .13 or .70 <= t < .71)))
    head = root[1]
    head.set('transform',f'translate(0 {-6*think+4*nod:.3f}) rotate({-9*think+17*turn:.3f} 252 270)')
    mouth = next(n for n in head[0][0] if n.get('d','').startswith('M242 245 Q252 251 262 245'))
    mouth.set('d',f'M242 245 Q252 {251-5*think:.3f} 262 245')
    eyes=head[1][0]
    eyes.set('transform', eyes.get('transform','')+f' translate({-7*think+14*turn:.3f} {-6*think:.3f})')
    # Both hands and the book move as one while he pauses to think.
    book = ET.Element(N+'g', {'transform': f'translate(0 {14*lower:.3f})'})
    for node in list(root)[2:]:
        root.remove(node); book.append(node)
    root.append(book)
    return ET.tostring(root,encoding='unicode')

def main():
    out=Path(sys.argv[1]); out.mkdir(parents=True,exist_ok=True)
    for i in range(FRAMES): (out/f'{i:03}.svg').write_text(make_frame(i/(FRAMES-1)),encoding='utf8')
if __name__ == '__main__': main()
