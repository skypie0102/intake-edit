"""Generate native vector parts and widget poses from the approved SVG.

The widget uses precomputed poses because launchers host RemoteViews. The app
animates the same named parts continuously. No bitmap generation is required.
"""
from copy import deepcopy
from pathlib import Path
import xml.etree.ElementTree as ET
import math
from svg_to_android import svg_to_vector

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/assets/companion/approved_state_concepts.svg'
OUTPUT = ROOT / 'app/src/main/res/drawable'
SVG = 'http://www.w3.org/2000/svg'
N = '{' + SVG + '}'
ET.register_namespace('', SVG)
MOODS = ('new', 'happy', 'waiting', 'hungry', 'sad')
PARTS = ('head', 'eyes', 'blink', 'left_arm', 'right_arm', 'symbol')
POSES = (0, 0, .1, .3, .55, .8, 1, 1, 1, 1, 1, 1, 1, .9, .6, 0)
PHASES = (0, .04, .09, .14, .19, .24, .29, .34, .40, .48, .56, .64, .72, .8, .9, 0)
DOTS = ((329, 207), (339, 195), (348, 181))
QUESTION_CURVE = 'M352 161 Q348 152 352 144 Q356 136 367 137 Q381 138 382 150 Q382 160 369 166 Q363 169 364 176'

def question_length():
    points = [(352,161), (348,152), (352,144), (356,136), (367,137),
              (381,138), (382,150), (382,160), (369,166), (363,169), (364,176)]
    length = 0
    for i in range(0, 10, 2):
        a, b, c = points[i:i+3]
        previous = a
        for step in range(1, 101):
            t = step / 100
            point = tuple((1-t)**2*a[k] + 2*(1-t)*t*b[k] + t*t*c[k] for k in (0, 1))
            length += math.dist(previous, point)
            previous = point
    return length

QUESTION_LENGTH = question_length()

# One continuous limb; both poses have the same cubic segments and attachment.
ARM_REST = (303,269, 303,255,310,245,322,247, 330,248,335,253,334,262, 334,266,332,270,330,272)
ARM_THINK = (303,269, 293,263,282,256,277,248, 272,241,265,239,262,245, 259,251,266,264,282,273)


def arm_path(progress):
    if not 0 <= progress <= 1:
        raise ValueError('arm progress must be between zero and one')
    v = [a + (b-a)*progress for a,b in zip(ARM_REST, ARM_THINK)]
    return (('M%.2f %.2f C%.2f %.2f %.2f %.2f %.2f %.2f '
             'C%.2f %.2f %.2f %.2f %.2f %.2f C%.2f %.2f %.2f %.2f %.2f %.2f') % tuple(v)
            + ' Z')


def scene(mood):
    original = ET.parse(SOURCE).getroot()
    scenes = [child for child in original if child.tag == N+'svg']
    return original, scenes[MOODS.index(mood)]


def svg_root(original):
    root = ET.Element(N+'svg', {'width':'320','height':'320','viewBox':'97 92 310 310'})
    root.append(deepcopy(original[0]))
    return root


def parts(mood):
    original, art = scene(mood)
    body = art[0][0]
    elements = list(body)
    eye_ids = {'new':{2,3,4,5}, 'happy':{2,3,4,5}, 'waiting':{2,3,4,5},
               'hungry':{2,3,4}, 'sad':{3,4,5,6}}[mood]

    def body_part(selected):
        wrapper = ET.Element(N+'g', {'clip-path':'url(#behind-book)'})
        group = ET.SubElement(wrapper, N+'g', {'transform':body.get('transform','')})
        for child in selected: group.append(deepcopy(child))
        return wrapper

    head = body_part([child for i,child in enumerate(elements[:-1]) if i not in eye_ids])
    eyes = body_part([child for i,child in enumerate(elements[:-1]) if i in eye_ids])
    if mood == 'hungry':
        blink = deepcopy(eyes)  # This mood is already half-lidded.
    else:
        y = 223 if mood == 'sad' else 218 if mood == 'happy' else 219
        lids = ET.Element(N+'path', {'d':f'M214 {y} Q224 {y+9} 234 {y} M271 {y} Q281 {y+9} 291 {y}',
                                      'fill':'none','stroke':'#493552','stroke-width':'5','stroke-linecap':'round'})
        blink = body_part([lids])
    arm = elements[-1]
    left, right = arm.get('d').split(' M',1)
    # Keep the approved limb shapes; lower each one so the book hides its base.
    left_node = deepcopy(arm); left_node.set('d', left + ' Z')
    right_node = deepcopy(arm); right_node.set('d', 'M' + right + ' Z')
    if mood == 'waiting': right_node.set('d', arm_path(0))
    for node in (left_node, right_node):
        node.set('transform', 'translate(0 10)')
    return original, art, {'head':head, 'eyes':eyes, 'blink':blink,
                           'left_arm':body_part([left_node]), 'right_arm':body_part([right_node]),
                           'symbol':[deepcopy(x) for x in list(art)[7:]],
                           'book':[deepcopy(x) for x in list(art)[1:7]]}


def svg_for(mood, selected, progress=0, blinking=False, show_symbol=False, phase=None):
    original, art, layers = parts(mood)
    root = svg_root(original)
    for part in selected:
        if part == 'head_group':
            group = ET.SubElement(root,N+'g',{'transform':f'rotate({1.5*progress:.2f} 252 270)'})
            group.append(deepcopy(layers['head']))
            group.append(deepcopy(layers['blink' if blinking else 'eyes']))
        elif part == 'right_arm' and mood == 'waiting' and progress:
            arm = deepcopy(layers[part])
            arm[0][0].set('d',arm_path(progress))
            root.append(arm)
        elif part == 'symbol' and show_symbol:
            for node in layers[part]: root.append(deepcopy(node))
        elif part == 'symbol' and mood == 'waiting' and phase is not None:
            if phase < .85:
                for index, (x, y) in enumerate(DOTS):
                    if phase >= (.14, .24, .34)[index]:
                        ET.SubElement(root, N+'circle', {'cx':str(x), 'cy':str(y), 'r':'2.5', 'fill':'#dcc2ec'})
                draw = max(0, min(1, (phase - .40) / .24))
                if draw > 0:
                    ET.SubElement(root, N+'path', {'d':QUESTION_CURVE, 'fill':'none', 'stroke':'#dcc2ec',
                        'stroke-width':'5', 'stroke-linecap':'round', 'stroke-linejoin':'round',
                        'stroke-dasharray':f'{QUESTION_LENGTH:.3f} {QUESTION_LENGTH:.3f}', 'stroke-dashoffset':f'{QUESTION_LENGTH*(1-draw):.3f}', 'data-trim':str(draw)})
                if phase >= .64:
                    ET.SubElement(root, N+'circle', {'cx':'364', 'cy':'186.5', 'r':'2.5', 'fill':'#dcc2ec'})
        elif part == 'symbol':
            continue  # Symbols appear only with their corresponding actions.
        elif isinstance(layers[part], list):
            for node in layers[part]: root.append(deepcopy(node))
        else:
            root.append(deepcopy(layers[part]))
    return ET.tostring(root,encoding='unicode')


def write_vector(path, svg):
    path.write_text(svg_to_vector(svg),encoding='utf-8')


def generate():
    OUTPUT.mkdir(parents=True,exist_ok=True)
    for mood in MOODS:
        for part in PARTS:
            write_vector(OUTPUT/f'companion_rig_{mood}_{part}.xml',svg_for(mood,[part],show_symbol=part == 'symbol'))
        indices = range(len(POSES)) if mood == 'waiting' else (0, 3)
        for i in indices:
            amount = POSES[i]
            progress = amount if mood == 'waiting' else 0
            svg = svg_for(mood,['head_group','left_arm','right_arm','book','symbol'],progress,blinking=i==3,phase=PHASES[i] if mood == 'waiting' else None)
            write_vector(OUTPUT/f'companion_widget_{mood}_{i:02d}.xml',svg)
    write_vector(OUTPUT/'companion_rig_book.xml',svg_for('waiting',['book']))


if __name__ == '__main__':
    generate()
    print('Generated companion rig parts and widget poses from the approved SVG.')
