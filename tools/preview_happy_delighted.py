"""Standalone study: the Happy companion is delighted by a passage.

Creates SVG frames without altering the app animation pack.
"""
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
import generate_companion_rig as rig

N = rig.N
FRAMES = 144
FRAME_MS = 80


def envelope(t, start, fade_in, finish, fade_out):
    return rig.smooth((t-start)/fade_in) * rig.smooth((finish-t)/fade_out)


def make_frame(t):
    if t >= 1:
        t = 0
    reading = envelope(t, .025, .09, .42, .12) + envelope(t, .85, .08, 1, .08)
    delight = envelope(t, .33, .10, .88, .13)
    hug = envelope(t, .44, .13, .83, .14)
    root = ET.fromstring(rig.svg_for('happy', ['head_group', 'left_arm', 'book', 'right_arm'],
        phase=0, idle_phase=t, view_box='92 87 320 320',
        blinking=(.30 <= t < .31 or .88 <= t < .89), action='shared_reading'))
    head = root[1]
    head.set('transform', f'translate(0 {2*reading-1.5*delight:.3f}) rotate({-1.2*delight:.3f} 252 270)')
    eyes = head[1][0]
    scan = 3.5*math.sin(2*math.pi*t*2.4)*reading
    eyes.set('transform', eyes.get('transform', '') + f' translate({scan:.3f} {3.8*reading:.3f})')
    for node in eyes:
        if node.tag == N+'ellipse':
            node.set('rx', f'{7+1.0*delight:.3f}')
            node.set('ry', f'{10+.7*delight:.3f}')
        elif node.tag == N+'circle':
            node.set('r', f'{2.3+.7*delight:.3f}')
    # The mouth and tongue widen together; the monocle remains attached to the head.
    for node in head[0][0]:
        if node.get('fill') in ('#57364f', '#ef9eaa'):
            scale = 1 + .10*delight
            node.set('transform', f'translate(252 248) scale({scale:.4f}) translate(-252 -248)')
    # Book, bookmark, and both hands move together as he draws them close.
    if hug > .00001:
        for child in list(root)[2:]:
            index = list(root).index(child)
            wrapper = ET.Element(N+'g', {'transform':f'translate(0 {-4.0*hug:.3f})'})
            root.remove(child)
            wrapper.append(child)
            root.insert(index, wrapper)
    # Two independently twinkling stars, introduced after he notices the passage.
    for start, finish, x, y, radius, phase in ((.47,.83,350,176,8.5,0), (.54,.88,153,207,5.6,1.1)):
        visible = envelope(t, start, .06, finish, .10)
        if visible > .0001:
            pulse = .9 + .1*math.sin((t-start)*math.pi*9 + phase)
            scale = visible*pulse
            group = ET.SubElement(root, N+'g', {'transform':f'translate({x} {y}) scale({scale:.4f})'})
            ET.SubElement(group, N+'path', {
                'd':f'M0 {-radius:.2f} Q{radius*.22:.2f} {-radius*.22:.2f} {radius:.2f} 0 '
                    f'Q{radius*.22:.2f} {radius*.22:.2f} 0 {radius:.2f} '
                    f'Q{-radius*.22:.2f} {radius*.22:.2f} {-radius:.2f} 0 '
                    f'Q{-radius*.22:.2f} {-radius*.22:.2f} 0 {-radius:.2f} Z',
                'fill':'#efcce4', 'stroke':'#b99acf', 'stroke-width':'1.4', 'stroke-linejoin':'round'})
    return ET.tostring(root, encoding='unicode')


def main():
    output = Path(sys.argv[1])
    output.mkdir(parents=True, exist_ok=True)
    for i in range(FRAMES):
        (output / f'{i:03}.svg').write_text(make_frame(i/(FRAMES-1)), encoding='utf8')

if __name__ == '__main__':
    main()
