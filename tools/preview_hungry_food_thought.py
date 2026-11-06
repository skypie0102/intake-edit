"""Standalone Hungry idle: food distracts him from reading."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
import generate_companion_rig as rig

N = rig.N
FRAMES = 196
FRAME_MS = 80


def make_frame(t):
    if t >= 1:
        t = 0
    blink = .075 <= t < .085 or .80 <= t < .81
    root = ET.fromstring(rig.svg_for('hungry', ['head_group', 'left_arm', 'right_arm', 'book'],
        phase=t, idle_phase=0, view_box='92 87 320 320', blinking=blink))
    # A slow reading glance fades away as the food distracts him.
    read = rig.smooth(t/.04) * rig.smooth((.20-t)/.10)
    gaze = -3*read + 5*rig.smooth((t-.05)/.10)*read
    eyes = root[1][1][0]
    eyes.set('transform', eyes.get('transform', '') + f' translate({gaze:.3f} {3*read:.3f})')
    # His pupils turn toward the food while the eyelids stay in place.
    look, _ = rig.hungry_action(t)
    if not blink:
        for node in eyes:
            if node.tag == N+'ellipse':
                node.set('cx', f'{float(node.get("cx"))+3*look:.3f}')
    # Reuse the production illustration, with gentle reveals for each element.
    symbols = ET.fromstring(rig.svg_for('hungry', ['symbol'], phase=t, view_box='92 87 320 320'))
    for node in list(symbols)[1:]:
        if node.tag == N+'circle':
            threshold = {'333': .20, '340': .26, '347': .32}.get(node.get('cx'), .2)
            node.set('opacity', f'{rig.smooth((t-threshold)/.025):.3f}')
        elif node.tag == N+'g' and node.get('opacity') and node.get('transform') is None:
            node.set('opacity', f'{float(node.get("opacity"))*rig.smooth((t-.38)/.045):.3f}')
            for index, child in enumerate(list(node)):
                if index > 0:
                    threshold = .44 if index < 4 else .50
                    child.set('opacity', f'{rig.smooth((t-threshold)/.035):.3f}')
        root.append(node)
    return ET.tostring(root, encoding='unicode')


def main():
    out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
    for i in range(FRAMES):
        (out / f'{i:03}.svg').write_text(make_frame(i/(FRAMES-1)), encoding='utf8')

if __name__ == '__main__':
    main()
