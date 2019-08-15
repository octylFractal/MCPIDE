#!/usr/bin/env bash
for s in *.svg; do
    convert -gravity center -resize 256x256 -extent 256x256 -background none -fill '#BABABA' -opaque black "$s" "${s%%.svg}".png
done
