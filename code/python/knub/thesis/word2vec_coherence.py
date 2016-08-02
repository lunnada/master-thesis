import argparse
import codecs
import logging
import os
import sys
from itertools import combinations
from math import factorial

import mkl
from gensim.models.word2vec import Word2Vec


def write_n_most_similar_words_for_each_word(file_name, word2vec, known_words, n):
    nr_known_words = len(known_words)
    output_every = nr_known_words / 1000
    with codecs.open(file_name, "w", encoding="utf-8") as f:
        for i, word in enumerate(known_words):
            if i % output_every == 0:
                print str(i * 100 / nr_known_words) + "%",
                sys.stdout.flush()
            try:
                most_similars = word2vec.most_similar(positive=[word], topn=n)
                most_similars = [(sim_word, prob) for sim_word, prob in most_similars if sim_word in known_words]
                for similar_word, prob in most_similars:
                    f.write(word + "\t" + similar_word + "\t" + str(prob) + "\n")
            except KeyError as e:
                print e


def write_all_pairwise_similarities(file_name, word2vec, known_words):
    nr_known_words = len(known_words)
    N = factorial(nr_known_words) / (2 * factorial(nr_known_words - 2))
    print str(N) + " pairs"
    output_every = N / 1000

    print "Calculating all pairwise similarities"
    with codecs.open(file_name, "w", encoding="utf-8") as f:
        for i, (word1, word2) in enumerate(combinations(known_words, r=2)):
            if i % output_every == 0:
                print str(i * 100 / N) + "%",
                sys.stdout.flush()
            try:
                sim = word2vec.similarity(word1, word2)
                if sim > 0.5:
                    f.write(word1 + "\t" + word2 + "\t" + str(sim) + "\n")
            except KeyError as e:
                print e


def calculate_similarities(word2vec, topic_model, all_pairwise):
    print "Loading words"
    words = [line.rstrip('\n') for line in open(topic_model + ".vocab")]
    nr_words = len(words)
    print str(nr_words) + " words"

    print "Known words:"
    known_words = {word for word in words if word in word2vec}
    print str(len(known_words)) + " known words"

    if all_pairwise:
        write_all_pairwise_similarities(topic_model + ".similarities-all", word2vec, known_words)
    else:
        write_n_most_similar_words_for_each_word(topic_model + ".similarities-most-similar", word2vec, known_words, 20)


def calculate_word2vec_topic_coherence(word2vec, topic_model, embedding_model):
    with open(topic_model + ".ssv", "r") as lines:
        with open(topic_model + "." + os.path.basename(embedding_model) + ".ssv", "w") as output:
            for line in lines:
                if "topic-count" in line:  # skip first header line
                    continue
                split = line.split(" ")

                words = split[-10:]
                for word in words:
                    try:
                        most_similars = word2vec.most_similar([word], topn=9)
                        most_similars = map(lambda t: t[0], most_similars)
                        most_similars = map(lambda s: s.encode('utf8'), most_similars)
                        most_similars.insert(0, word)
                        output.write("%s\n" % " ".join(most_similars))
                        continue
                    except KeyError:
                        pass


def main():
    logging.basicConfig(format='%(asctime)s : %(levelname)s : %(message)s', level=logging.INFO)
    mkl.set_num_threads(3)

    parser = argparse.ArgumentParser("Create topics from word2vec model")
    parser.add_argument("--embedding-model", type=str)
    parser.add_argument("--topic-model", type=str)
    args = parser.parse_args()

    assert "topic" in args.topic_model, "'%s' not a topic model" % args.topic_model
    assert "embedding" in args.embedding_model, "'%s' not an embedding model" % args.embedding_model

    word2vec = Word2Vec.load_word2vec_format(args.embedding_model, binary=True)

    # calculate_similarities(word2vec, args.topic_model, all_pairwise=False)
    calculate_word2vec_topic_coherence(word2vec, args.topic_model, args.embedding_model)


if __name__ == "__main__":
    main()

    # frequent_words = [line.rstrip('\n') for line in open("/data/wikipedia/2016-06-21/vocab.txt")]
    # frequent_words = frequent_words[10000:-40000]
    # print "%d frequent words" % len(frequent_words)
    # random_sample = [frequent_words[i] for i in sorted(random.sample(xrange(len(frequent_words)), 100000))]
    #
    # with open(args.embedding_model + ".similars", "w") as output:
    #     for word in random_sample:
    #         try:
    #             similars = word2vec.most_similar([word], topn=3)
    #             for similar, prob in similars:
    #                 output.write("%s\t%s\t%s\n" % (word, similar, prob))
    #         except:
    #             pass
