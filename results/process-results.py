import sys
import re
from collections import defaultdict
import numpy

if len(sys.argv) >= 2:
    FILE = sys.argv[1]
else:
    FILE = "20170899-final.csv"

if len(sys.argv) >= 3:
    OUTPUT = sys.argv[2]
else:
    # E.g. 20170524T1416-e1ba09dd-serenity.csv to
    # 20170524T1416-e1ba09dd-serenity-medians.csv
    OUTPUT = re.sub(r"(\.[^.]+)$", r"-medians\1", FILE)

def parse_file(filename):
    results = defaultdict(list)  # (version, w, s) -> [time]
    version_values = set()
    w_values = set()
    s_values = set()
    with open(filename, "r", encoding="utf-8") as file:
        file.readline()  # skip header
        for line in file:
            if line.strip() == "":
                continue
            try:
                version, w, s, i, time = line.split(",")
                # time is suffixed with \n
            except ValueError:
                print("Error: could not read line:\n%s" % line)
            w = int(w)
            if s != "None":
                s = int(s)
            time = float(str(time).strip())
            results[(version, w, s)].append(time)
            version_values.add(version)
            w_values.add(w)
            s_values.add(s)

    medians = {}  # (version, w, s) -> median time
    for k, times in results.items():
        medians[k] = numpy.median(times)

    out = ""
    for k in sorted(medians.keys()):
        out += "%s,%s,%s,%s\n" % (k[0], k[1], k[2], medians[k])
    return out

out = parse_file(FILE)
with open(OUTPUT, "x") as f:
    f.write(out)
