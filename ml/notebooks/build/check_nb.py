import json
import sys

bad = 0
for path in sys.argv[1:]:
    nb = json.load(open(path, encoding="utf-8"))
    for i, c in enumerate(nb["cells"]):
        if c["cell_type"] != "code":
            continue
        out, cont = [], False
        for line in "".join(c["source"]).splitlines():
            s = line.strip()
            if cont:  # dong noi tiep cua lenh shell
                cont = line.rstrip().endswith("\\")
                continue
            if s.startswith(("!", "%")):
                out.append(line[: len(line) - len(line.lstrip())] + "pass")
                cont = line.rstrip().endswith("\\")
                continue
            out.append(line)
        try:
            compile("\n".join(out), f"{path}#cell{i}", "exec")
        except SyntaxError as e:
            bad += 1
            print("SYNTAX", path, "cell", i, e)
    print(path, "OK" if not bad else "")
sys.exit(1 if bad else 0)
