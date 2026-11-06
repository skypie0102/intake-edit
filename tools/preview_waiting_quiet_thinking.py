"""Standalone Waiting idle: looking up and quietly thinking."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
import generate_companion_rig as rig

N = rig.N
FRAMES = 180
FRAME_MS = 80


def window(t, start, rise, end, fall):
    return rig.smooth((t-start)/rise) * rig.smooth((end-t)/fall)


def thinking_amount(t):
    return window(t, .08, .14, .86, .16)


def make_frame(t):
    if t >= 1:
        t = 0
    thought = thinking_amount(t)
    arm = window(t, .12, .16, .84, .16)
    # The existing single hand moves to the chin and returns to the book.
    root = ET.fromstring(rig.svg_for('waiting', ['head_group', 'left_arm', 'right_arm', 'book'],
        progress=arm, phase=0, idle_phase=0, view_box='92 87 320 320',
        blinking=(.075 <= t < .085 or .865 <= t < .875), action='shared_reading'))
    head = root[1]
    head.set('transform', f'translate(0 {-2.5*thought:.3f}) rotate({-3*thought:.3f} 252 270)')
    eyes = head[1][0]
    # Look up to the right, drift left briefly as he considers, then back.
    shift = rig.smooth((t-.42)/.10) * rig.smooth((.67-t)/.10)
    eyes.set('transform', eyes.get('transform', '') + f' translate({5*thought-3*shift:.3f} {-5*thought:.3f})')
    # Use the existing thought-dot and question-mark artwork and reveal order.
    phase = max(0, min(.84, (t-.23)/.50))
    if t >= .23 and t < .80:
        fade = rig.smooth((.80-t)/.09)
        symbols = ET.fromstring(rig.svg_for('waiting', ['symbol'], phase=phase, view_box='92 87 320 320'))
        g = ET.SubElement(root, N+'g', {'opacity': f'{fade:.3f}'})
        for node in list(symbols)[1:]:
            g.append(node)
    return ET.tostring(root, encoding='unicode')


def main():
    out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
    for i in range(FRAMES):
        (out / f'{i:03}.svg').write_text(make_frame(i/(FRAMES-1)), encoding='utf8')

if __name__ == '__main__':
    main()
