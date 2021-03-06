import gensim
import pandas as pnd
from sklearn.decomposition import RandomizedPCA
from sklearn.manifold import TSNE
from collections import OrderedDict
import os

def parse_params(s):
    dirname = os.path.basename(os.path.dirname(s))
    basename = os.path.basename(s)

    name_parts_dir = dirname.split(".")
    name_parts_file = basename.split(".")
    params = OrderedDict()
    for part in name_parts_dir + name_parts_file:
        split = part.split("-")
        if len(split) == 2 and split[0] != "skip" and split[0] != "50":
            try:
                params[split[0]] = split[1]
            except ValueError:
                pass
        elif len(split) == 3:
            try:
                v = float(".".join(split[-2:]))
                params[split[0]] = str(v)
            except ValueError:
                pass
    return params


def pca(embeddings, n=2):
    trained_pca = RandomizedPCA(n_components=n, random_state=2101991)
    return trained_pca.fit_transform(embeddings)


def tsne(embeddings, n=2):
    tsne = TSNE(n_components=n)
    return tsne.fit_transform(embeddings)


def tsne_with_init_pca(embeddings, n=2):
    tsne = TSNE(n_components=n, init="pca")
    return tsne.fit_transform(embeddings)


def find_files(folder, *filters):
    l = os.listdir(folder)
    l = [f for f in l if all([filter in f for filter in filters])]
    l = [folder + "/" + f for f in l]
    return l


SKIP_GRAM_VECTOR_FILE = "/home/knub/Repositories/master-thesis/models/embedding-models/dim-200.skip-gram.embedding"
SKIP_GRAM_VECTOR_REMOTE_FILE = "/data/wikipedia/2016-06-21/embedding-models/dim-200.skip-gram.embedding"
WORD2VEC_VECTOR_FILE = "/home/knub/Repositories/master-thesis/models/embedding-models/google.embedding"


def load_embedding_model(model):
    return gensim.models.Word2Vec.load_word2vec_format(model, binary=True)


def load_skip_gram():
    if os.path.exists(SKIP_GRAM_VECTOR_FILE):
        return gensim.models.Word2Vec.load_word2vec_format(SKIP_GRAM_VECTOR_FILE, binary=True)
    elif os.path.exists(SKIP_GRAM_VECTOR_REMOTE_FILE):
        return gensim.models.Word2Vec.load_word2vec_format(SKIP_GRAM_VECTOR_REMOTE_FILE, binary=True)
    else:
        raise Exception("no skip-gram model to load")


def load_word2vec():
    return gensim.models.Word2Vec.load_word2vec_format(WORD2VEC_VECTOR_FILE, binary=True)


class TopicModelLoader:

    def __init__(self, model, vectors):
        self.model = model
        self.vectors = vectors
        self.prob_columns = None
        self.topic_words = None
        self.sim_functions = ["max", "sum", "bhattacharyya", "hellinger", "jensen-shannon"]

    def load_topic_probs(self):
        df_probs = pnd.read_csv(self.model + ".topic-probs-normalized")
        self.prob_columns = map(str, list(range(len(df_probs.columns) - 2))) # - "word" and "word-prob"
        return df_probs

    def load_topics(self):
        df_topics = pnd.read_csv(self.model + ".ssv", sep=" ", encoding="utf-8")
        self.topic_words = set(df_topics.ix[:,-10:].values.flatten())
        return df_topics

    def load_all_topic_similars(self):
        dfs = dict()
        for sim_function in self.sim_functions:
            df_sim = self.load_topic_similars(sim_function)
            df_sim["we_sim"] = df_sim[["word", "similar_word"]].apply(
                lambda x: self.get_similarity(x["word"], x["similar_word"], self.vectors), axis=1)
            df_sim = df_sim[df_sim["we_sim"] != -1]

            dfs[sim_function] = df_sim

        return dfs

    def load_topic_similars(self, type):
        df_similars = pnd.read_csv(self.model + ".similars-%s" % type, sep="\t", header=None, encoding="utf-8")
        df_similars["tm_sim"] = df_similars[0]
        del df_similars[0] # delete original similarity column
        # del df_similars[1] # delete "SIM" column
        df_similars.columns = ["word", "similar_word", "tm_sim"]
        return df_similars

    def get_similarity(self, word1, word2, v):
        # ugly but works for now
        if word1 not in v:
            if word1.upper() in v:
                word1 = word1.upper()
            if word1.title() in v:
                word1 = word1.title()
        if word2 not in v:
            if word2.upper() in v:
                word2 = word2.upper()
            if word2.title() in v:
                word2 = word2.title()
        try:
            return v.similarity(word1, word2)
        except KeyError:
            return -1.0
