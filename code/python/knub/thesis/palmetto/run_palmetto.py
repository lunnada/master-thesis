import argparse
from datetime import datetime
import numpy as np
import re
import subprocess

from knub.thesis.util import *


def parse_topic_coherence(stdout):
    topic_coherences = []

    for line in stdout.splitlines():
        m = re.search("[01]\.\d{5}", line)
        if m:
            tc = float(m.group(0))
            topic_coherences.append(tc)

    # assert len(topic_coherences) in {2 ** i for i in range(1, 10)}, str(
    #     len(topic_coherences)) + " is not a power of two"
    return topic_coherences


def create_palmetto_file(topic_file):
    palmetto_file = topic_file + ".palmetto"
    with open(topic_file, "r") as input:
        with open(palmetto_file, "w") as output:
            for line in input:
                split = line.split(" ")
                if "topic-count" not in line: # skip first line
                    new_line = " ".join(split[-10:])
                    output.write(new_line)
    return palmetto_file


def calculate_topic_coherences(f):
    palmetto_file = create_palmetto_file(f)
    local_palmetto = "/home/knub/Repositories/Palmetto/target/Palmetto-jar-with-dependencies.jar"
    remote_palmetto = "/home/stefan.bunk/Palmetto/target/Palmetto-jar-with-dependencies.jar"
    local_wikidata = "/home/knub/Repositories/Palmetto/wikipedia_bd"
    remote_wikidata = "/data/wikipedia/2016-06-21/palmetto/wikipedia_bd"
    if os.path.exists(local_palmetto):
        p = subprocess.Popen(
            ["java",
             "-jar",
             local_palmetto,
             local_wikidata,
             "C_V",
             palmetto_file],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    elif os.path.exists(remote_palmetto):
        p = subprocess.Popen(
            ["java",
             "-jar",
             remote_palmetto,
             remote_wikidata,
             "C_V",
             palmetto_file],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    else:
        raise Exception("Palmetto not found")


    stdout, stderr = p.communicate()
    os.remove(palmetto_file)
    error = p.returncode
    if error:
        print stderr
        print "-" * 100
        print stdout
        return RuntimeError("Could not compute topics")
    else:
        topic_coherences = parse_topic_coherence(stdout)
        return topic_coherences


def main():
    parser = argparse.ArgumentParser("Evaluating word2vec with analogy task")
    parser.add_argument("topic_files", type=str, nargs="+")
    args = parser.parse_args()

    now = datetime.now()

    print now.strftime("%a, %Y-%m-%d %H:%M")

    for topic_file in args.topic_files:
        params_str = "\t".join(parse_params(topic_file).values())
        topic_coherences = calculate_topic_coherences(topic_file)
        if params_str:
            print "%s\t%s\t%.3f\t%.3f" % (topic_file, params_str, np.mean(topic_coherences), np.std(topic_coherences))
        else:
            print "%s\t%.3f\t%.3f" % (topic_file, np.mean(topic_coherences), np.std(topic_coherences))


if __name__ == "__main__":
    main()
