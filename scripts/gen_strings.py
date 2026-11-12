import sys, os
from xml.sax.saxutils import escape
def write(res, name, table):
    # table: {key: {lang: text}}; lang '' = default (en)
    langs = {'':'values','tr':'values-tr','es':'values-es','zh':'values-zh','pl':'values-pl'}
    for lang, d in langs.items():
        lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
        for k, v in table.items():
            t = v.get(lang if lang else 'en')
            if t is None: continue
            t = escape(t).replace("'", "\\'")
            lines.append(f'    <string name="{k}">{t}</string>')
        lines.append('</resources>')
        os.makedirs(os.path.join(res, d), exist_ok=True)
        open(os.path.join(res, d, name), 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
