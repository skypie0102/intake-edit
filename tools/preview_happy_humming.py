"""Standalone Happy humming study, using the production companion rig.

Writes SVG frames to an output directory; this does not change the app animation pack.
"""
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
import generate_companion_rig as rig

N = rig.N
FRAMES = 144
FRAME_MS = 80


def bell(t, start, fade_in, finish, fade_out):
    return rig.smooth((t - start) / fade_in) * rig.smooth((finish - t) / fade_out)


def make_frame(t):
    # The closing frame is exactly the initial rest pose.
    if t >= 1:
        t = 0
    # Ease in and out of each movement; the first and final poses are identical.
    looking = bell(t, .025, .09, .30, .10) + bell(t, .87, .08, 1.0, .08)
    singing = bell(t, .23, .10, .83, .10)
    lift = rig.smooth((t - .16) / .13) * rig.smooth((.91 - t) / .13)
    # A slow, small head tilt; fades into the rest pose at both ends.
    tilt = 2.0 * math.sin(2 * math.pi * (t - .28) * 2.1) * singing
    motion_phase = t if 0 < t < 1 else 0
    root = ET.fromstring(rig.svg_for('happy', ['head_group', 'left_arm', 'book', 'right_arm'],
        phase=0, idle_phase=motion_phase, view_box='92 87 320 320',
        blinking=(.145 <= t < .155 or .835 <= t < .845), action='shared_reading'))
    head = root[1]
    head.set('transform', f'translate(0 {-2.2*lift + 2.2*looking:.3f}) rotate({tilt:.3f} 252 270)')
    # Pupils and highlights move together, while the monocle remains on the head.
    eyes = head[1][0]
    eyes.set('transform', eyes.get('transform', '') + f' translate({1.5*math.sin(2*math.pi*t*2.0)*looking:.3f} {4.0*looking:.3f})')
    # Keep the native smiling mouth silhouette, gently varying its width and height.
    mouth = next(x for x in head[0][0] if x.get('fill') == '#57364f')
    tongue = next(x for x in head[0][0] if x.get('fill') == '#ef9eaa')
    hum = singing * (.5 + .5*math.sin(2 * math.pi * (t-.30) * 4.0))
    a = (239,239,252,245,265,239,263,257,252,258,241,257,239,239)
    b = (246,241,252,242,258,241,258,251,252,253,246,251,246,241)
    v = [x+(y-x)*hum for x,y in zip(a,b)]
    mouth.set('d', ('M%.2f %.2f Q%.2f %.2f %.2f %.2f Q%.2f %.2f %.2f %.2f Q%.2f %.2f %.2f %.2f Z') % tuple(v))
    tongue.set('opacity', f'{1-hum:.3f}')
    # Two notes, independently timed, enter gently and float upward.
    for start, x, y, scale in ((.34, 352, 203, .85), (.57, 370, 197, .72)):
        duration = .19
        local = (t-start)/duration
        if 0 < local < 1:
            opacity = rig.smooth(local/.22) * rig.smooth((1-local)/.30)
            group = ET.SubElement(root, N+'g', {'transform':f'translate({x:.2f} {y-16*local:.2f}) scale({scale:.2f})', 'opacity':f'{opacity:.3f}'})
            ET.SubElement(group, N+'ellipse', {'cx':'0','cy':'0','rx':'5','ry':'3.2','transform':'rotate(-20)', 'fill':'#c6a5df'})
            ET.SubElement(group, N+'path', {'d':'M4 0 L4 -17 Q9 -17 12 -13', 'fill':'none', 'stroke':'#c6a5df', 'stroke-width':'3', 'stroke-linecap':'round', 'stroke-linejoin':'round'})
    return ET.tostring(root, encoding='unicode')


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    for frame in range(FRAMES):
        (out / f'{frame:03}.svg').write_text(make_frame(frame/(FRAMES-1)), encoding='utf-8')

if __name__ == '__main__':
    main()
