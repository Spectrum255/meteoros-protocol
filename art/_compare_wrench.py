from PIL import Image, ImageDraw

old = Image.open('art/sprites/wrench-vertical-old.png').convert('RGBA')
new = Image.open('art/sprites/wrench-45-preview-v2.png').convert('RGBA')
scale = 4
old = old.resize((old.width * scale, old.height * scale), Image.NEAREST)
new = new.resize((new.width * scale, new.height * scale), Image.NEAREST)
pad = 16
label_h = 40
w = old.width + new.width + pad * 3
h = max(old.height, new.height) + pad * 2 + label_h
out = Image.new('RGBA', (w, h), (245, 245, 245, 255))
out.paste(old, (pad, pad + label_h))
out.paste(new, (pad * 2 + old.width, pad + label_h))
d = ImageDraw.Draw(out)
d.text((pad, 12), 'BEFORE: vertical handle', fill=(20, 20, 20, 255))
d.text((pad * 2 + old.width, 12), 'AFTER: 45-degree axis x+y=15', fill=(20, 20, 20, 255))
out.save('art/sprites/wrench-before-after.png')
print(out.size)
