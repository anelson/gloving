---
layout: default
title: A few notes about word vectors
---

<!--<script src="http://underscorejs.org/underscore-min.js"></script>-->
<script src="https://cdnjs.cloudflare.com/ajax/libs/lodash.js/3.10.1/lodash.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/d3/3.5.6/d3.min.js" charset="utf-8"></script>
<script src="scripts/whisker-plot.js"></script>
<script src="scripts/viz.js"></script>

<!--<div class="home">-->

# Foreward

When I started this document, I set out to gather a few notes on my experiments with using word vectorization technologies GloVe and word2vec.  I expected to note the results of a few experiments and move on, however the project exploded in complexity as I kept going further and further down the analytical and experimental rabbit hole.  What you have before you is the result of about a week and a half of focused effort and analysis, during which time I was learning to use Apache Spark, d3.js, and of course working with the GloVe and word2vec technologies themselves.

# Introduction

A few years ago, Google published an [exciting paper](http://arxiv.org/pdf/1301.3781.pdf) describing a new model whereby computers can be trained to represent written words as vectors (basically arrays of numbers), such that certain semantic relationships between words can be expressed as relationships between their corresponding vectors.  The model and tools which implement it are published under the name ['word2vec'](https://code.google.com/p/word2vec/).

Apparently this idea had been around for a while, but until Google's `word2vec` I'd never heard of it.  The power of this model is that we have lots of powerful tools for dealing with vectors and the relationships between them, and now we can use those tools on written words easily.  The actual values of the vectors aren't important right now; it's just a sequence of numbers, but if you're curious how they're computed read the word2vec paper linked above.

For example, let's say we have a database of millions of words, and their corresponding vectors.  If `a` is set to the vector corresponding to the word "Athens", and `g` is set to the vector for "Greece", then we can easily compute vector `c = a-g`, and that vector `c` now represents, numerically, the semantic relationship between "Athens" and "Greece".  How is that useful?  Watch this:

Let `o` be the vector for the word "Oslo".  We want to know what word has the same relationship to "Oslo" as "Athens" has to "Greece".  We humans can tell that the relationship is that Athens is the capital of Greece, but the word2vec model knows this too, without having been told explicitly about capitals or cities or countries.  It turns out that if we query our huge database of word vectors, for the word whose vector is closest to `a - c + o`, the closest match will be the word for "Norway"!

This kind of task, called an analogy, comes up often in various problem domains.  It generally takes the form "A is to B as C is to ??".  In the example above, we asked "Athens is to Greece as Oslo is to ??", the answer being "Norway".  Any educated adult can solve analogy problems like this easily, but with this model, machines who have not been taught anything about grammar, vocabulary, or the relationships between concepts, can accurately solve them as well.  Here are some more examples from the word2vec paper:

<table>
  <tr>
    <th>A is to..</th>
    <th>...B...</th>
    <th>...as C is to...</th>
    <th>??</th>
  </tr>
  <tr><td>Astana</td><td>Kazakhstan</td><td>Harare</td><td>Zimbabwe</td></tr>
  <tr><td>Angola</td><td>kwanza</td><td>Iran</td><td>rial</td></tr>
  <tr><td>Chicago</td><td>Illinois</td><td>Stockton</td><td>California</td></tr>
</table>

It still seems like magic to me, that machines could solve these sorts of problems.  I would not have been able to solve any but the last example in the table above, but a word2vec trained model could.  Amazing!

# Word2Vec and GloVe

Word2vec isn't the only word vectorization model I've been experimenting with.  [GloVe](http://nlp.stanford.edu/projects/glove/) (as in 'GLObal VEctors for word representation") is another, very different model which in the end produces vectors for words with some of the same properties as word2vec.  According to the GloVe paper, the accuracy compared to word2vec for a given training environment is higher, where 'accuracy' is measured by the percentage of analogy problems each model solves correctly.  I recall reading some comments somewhere suggesting that there is some debate as to the fairness of the comparison used in the GloVe paper, which is why I've undertaken all of the following experiments using both models.

# Architectures and Models

Word2vec and GloVe describe architectures for training and using models that transform words into vectors.  In both cases, to actually construct those models requires training on a truly massive corpus of input data.  In this case, the data is text, the more the better.  The architecture describes how to feed this text into the models and refine the accuracy as training goes on.  This is a complex, error-prone, and very computationally intensive process.  For my experiments I have opted not to train my own models, but rather use the pre-trained models provided by both projects.

It's very important to understand the distinction between the word2vec or GloVe architecture, and the pre-trained word2vec or GloVe models I'm using for my experiments.  Both word2vec and GloVe can produce terrible, inconsistent, inaccurate, worthless results if they are trained improperly or without enough data or with data that doesn't adequately cover the concepts we're interested in.  For example, if you trained a word2vec model using the text of the Bible and of the collected works of Shakespeare, the resulting model will not be able to capture the relationship between "South Africa" and "apartheid" or "Kennedy" and "Apollo", since none of those concepts would appear in the training corpus.

Due to this complexity, I use only the pre-trained models, and for the rest of this article when I say "word2vec" or "GloVe" I am referring to the particular pre-trained models I'm using, not the architectures themselves.  Someone else could use the exact same architectures but with different training data and get wildly different results.  YMMV.

# Selection of Pre-trained Models

Here are the pre-trained models I'm using in these experiments:

<table>
  <tr>
    <th>Arch.</th>
    <th>Name</th>
    <th>Words</th>
    <th>Dims</th>
    <th>Inputs</th>
  </tr>

  {% for model in site.data.models %}
  <tr>
    <td class="nowrap">{{model.arch}}</td>
    <td class="nowrap"><pre>{{model.name}}</pre></td>
    <td class="nowrap">{{model.words}}</td>
    <td class="nowrap">{{model.dimensions}}</td>
    <td>{{model.corpus}}</td>
  </tr>
  {% endfor %}
</table>

In all cases these pre-trained models can be downloaded from the corresponding project's website.  As you can see, this covers a wide range of training corpus sizes, word counts, and dimensions (which means how many numbers are in each word's corresponding vector).  Dimensionality is of particular interest to me, because higher-dimensional data is much more expensive computationally, so I want to know if more dimensions has a material impact on the quality of the results.

# Source code

All of these experiments were performed using Scala code I wrote, with the Apache Spark framework handling most of the heavy lifting.  Due to the size of these datasets and the relative lack of power of my MacBook, some of the experiments were run on an Amazon EMR cluster.

I'm not going to focus on the code or how it is written here, it's rather straightforward for anyone who's familiar with Scala and Spark.  The full source is on GitHub.  I would point out that the word2vec binary format is retarded, and consequently the Scala code to load that format into a Spark RDD is similarly handicapped.

# Statistical Analysis

This section contains my analysis of the statistical properties of the word vectors produced by these models.  It's pretty heady, but it's full of cool charts, so it would be worth your time to study it if you want to understand in better detail what the vectors produced by these models look like.

## Distribution of Dimension Values
To start with, I wanted to understand what these models produced.  Let's start by looking at the smallest dataset, `glove.6b.50d`.  I computed the statistical summary for each of the 50 dimensions separately, and plotted them on a box-and-whiskers plot.  This gives us a sense of the distribution of the values in each dimension and how they compare to each other:

### glove.6B.50d

{% include model_histogram.html id="glove.6B.50d" %}

This is a dense chart, so take a moment to study it.  In all of these models, the vast majority of the values fall within a very narrow range, more or less +/- 1.0, however there are outliers that drive the range of possible values much higher.  Because of that I've modified the visualization so you can select to view the distributions of 100% of the values, the 96% of the values closest to the median, or the values within the range +/- 1.5 times the IQR (interquartile-range).

The important observation for this dataset is that all of the dimensions more or less have the same distribution, so there isn't any one dimension that will be substantially larger or smaller than the others.  Often high-dimensional data doesn't have this property, requiring some standardization of each dimension in order to make things like Euclidean distance between vectors have meaning, but in this case we're good without any standardization.

For completeness, you can see the same charts for the other models below.  Because they have so many dimensions, the charts become unwieldy very quickly, which is why I presented the 50-dimension chart first.  I didn't study the rest of these other than to satisfy myself that the numerical distribution was comparable.

### glove.6B.300d

{% include model_histogram.html id="glove.6B.300d" %}

Those 300 dimensions make this chart really wide, so you'll have to scroll to see all of them.  For the most part it's a smooth distribution consistent across all the dimensions, but there are some exceptions.  Dimension 9 and 34 and 244 and especially 276 have median values substantially above zero, while 150 and 200 are quite a bit below zero.  Whether or not this presents a problem in the results, we'll find out later.

### glove.42B.300d

{% include model_histogram.html id="glove.42B.300d" %}

Here again we see a few dimensions stepping out of line with an interquartile range entirely above or below zero, though to my eye it seems more smooth and consistent than `glove.6B.300d` did.  That doesn't surprise me, since this model was trained with about 7x more data than `glove.6B.300d`.

### GoogleNews-vectors-negative300

{% include model_histogram.html id="GoogleNews-vectors-negative300" %}

Look at how tight those interquartile-ranges are!  This is the only word2vec model I have, so I don't know if this distribution is characteristic of word2vec in general or if the Google News training corpus is responsible.  In any case, I like it.  96% of values are in the range (-0.45, 0.45).  Of course, a clean distribution doesn't automatically mean word2vec is superior; further testing is needed.

<div id="tooltip" class="hidden">
</div>

<script langauge="javascript">
var vectorAnalysisData = null

loadVectorAnalysisData( function(data) {
    vectorAnalysisData = data;

    {% for model in site.data.models %}
      drawDimensionsWhiskerPlot(data, "{{model.name}}");
    {% endfor %}
  });
</script>

<!--</div>-->
