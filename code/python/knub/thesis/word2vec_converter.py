import argparse
import logging
import os
from codecs import open

from gensim.models import Word2Vec

logging.basicConfig(format='%(asctime)s : %(levelname)s : %(message)s', level=logging.INFO)

if __name__ == "__main__":
    parser = argparse.ArgumentParser("Convert word2vec model from binary to txt")
    parser.add_argument("--embedding-model", type=str)
    parser.add_argument("--topic-model", type=str)
    args = parser.parse_args()

    model = Word2Vec.load_word2vec_format(args.embedding_model, binary=True)

    if args.topic_model is None:
        print "No topic model set, converting all words"
        model.save_word2vec_format(args.embedding_model + ".txt", binary=False)
    else:
        embedding_name = os.path.basename(args.embedding_model)
        print "Topic model is set, using only words from " + args.topic_model
        with open(args.topic_model.replace("/model", "/") + embedding_name + ".restricted.vocab.embedding.txt", "w", encoding="utf-8") as output:
            with open(args.topic_model + "." + embedding_name + ".restricted.vocab", "r", encoding="utf-8") as f:
                for line in f:
                    word = line.rstrip()
                    if word in model:
                        pass
                    elif word.capitalize() in model:
                        word = word.capitalize()
                    elif word.upper() in model:
                        word = word.upper()
                    try:
                        output.write(word + " ")
                        output.write(" ".join(map(str, model[word])))
                    except KeyError as e:
                        print word + " not in embedding model"
                        raise e
                    output.write("\n")

    logging.info(model.most_similar(positive=['woman', 'king'], negative=['man']))
    logging.info(model.doesnt_match("breakfast cereal dinner lunch".split()))
    logging.info(model.similarity('woman', 'man'))

