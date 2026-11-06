"""Standalone Waiting idle: the companion finds his place on the page."""
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
import generate_companion_rig as rig

N = rig.N
FRAMES = 176
FRAME_MS = 80


def envelope(t, start, fade_in, finish, fade_out):
    return rig.smooth((t-start)/fade_in) * rig.smooth((finish-t)/fade_out)


def gaze(t):
    # Read left to right, pause, retrace, then finish the line.
    keys = [(0,0), (.08,-4.5), (.22,4.5), (.30,4.5), (.42,-4.5),
            (.50,-4.5), (.64,4.5), (.70,4.5), (.80,0), (1,0)]
    for (a,x), (b,y) in zip(keys, keys[1:]):
        if t <= b:
            return x + (y-x)*rig.smooth((t-a)/(b-a))
    return 0


def make_frame(t):
    if t >= 1:
        t = 0
    reading = envelope(t, .015, .08, .84, .12)
    puzzled = envelope(t, .28, .10, .56, .12)
    nod = envelope(t, .70, .055, .81, .065)
    # Keep the book stationary while he follows the line with his eyes.
    root = ET.fromstring(rig.svg_for('waiting', ['head_group','left_arm','right_arm','book'],
        phase=0, idle_phase=t, view_box='92 87 320 320',
        blinking=(.46 <= t < .47 or .87 <= t < .88), action='shared_reading'))
    head = root[1]
    head.set('transform', f'translate(0 {1.3*reading+2.5*nod:.3f}) rotate({2.5*puzzled+1.0*nod:.3f} 252 270)')
    eyes = head[1][0]
    # Move eyes and their highlights as one layer; the monocle stays with the head.
    eyes.set('transform', eyes.get('transform','') + f' translate({gaze(t):.3f} {4.0*reading:.3f})')
    mouth = next(node for node in head[0][0] if node.get('d','').startswith('M242 245 Q252 251 262 245'))
    mouth.set('d', f'M242 245 Q252 {251+2*nod:.3f} 262 245')
    return ET.tostring(root, encoding='unicode')


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    for frame in range(FRAMES):
        (out/f'{frame:03}.svg').write_text(make_frame(frame/(FRAMES-1)), encoding='utf8')

if __name__ == '__main__':
    main()
