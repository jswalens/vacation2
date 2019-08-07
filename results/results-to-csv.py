import os, os.path
import sys
import re

if len(sys.argv) >= 2:
    DIRECTORY = sys.argv[1]
else:
    DIRECTORY = "20170899-final"

if len(sys.argv) >= 3:
    OUTPUT = sys.argv[2]
else:
    OUTPUT = DIRECTORY + ".csv"

def print_line(version, w, s, i, time):
    return "%s,%s,%s,%s,%s\n" % (version, w, s, i, time)

def parse_results_dir(dir_name):
    out = "version,w,s,i,time (ms)\n"
    errors = []

    for f_name in os.listdir(dir_name):
        if f_name == "info.txt":
            continue

        match = re.search(r'(.+)-w(\d+)(?:-s(\d+))?-i(\d+).txt', f_name)
        if match is None:
            errors.append("Ignoring %s: wrong file name." % f_name)
            continue
        version, w, s, i = match.groups()

        with open(os.path.join(dir_name, f_name)) as f:
            contents = f.read()
        matches = re.findall(r'Total execution time: ([\d.]+) ms', contents)
        if len(matches) != 1:
            errors.append("File %s has %i time measurements, expected 1." % \
                (f_name, len(matches)))
            continue
        time = matches[0]

        out += print_line(version, w, s, i, time)

    return out, errors

out, errors = parse_results_dir(DIRECTORY)

with open(OUTPUT, "x") as f:
    f.write(out)

if len(errors) != 0:
    print("ERRORS:")
    print("\n".join(errors))
