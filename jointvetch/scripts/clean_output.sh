#!/bin/bash

sed -i '' -e 's/^[^0-9].*$//' -e '/^$/d' *
