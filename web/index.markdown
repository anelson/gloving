---
layout: default
title: A few notes about word vectors
---

<!--<script src="http://underscorejs.org/underscore-min.js"></script>-->
<script src="https://cdnjs.cloudflare.com/ajax/libs/lodash.js/3.10.1/lodash.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/d3/3.5.6/d3.min.js" charset="utf-8"></script>
<script src='https://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS-MML_HTMLorMML'></script>
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

{% include model_boxplot.html id="glove.6B.50d" %}

This is a dense chart, so take a moment to study it.  In all of these models, the vast majority of the values fall within a very narrow range, more or less +/- 1.0, however there are outliers that drive the range of possible values much higher.  Because of that I've modified the visualization so you can select to view the distributions of 100% of the values, the 96% of the values closest to the median, or the values within the range +/- 1.5 times the IQR (interquartile-range).

The important observation for this dataset is that all of the dimensions more or less have the same distribution, so there isn't any one dimension that will be substantially larger or smaller than the others.  Often high-dimensional data doesn't have this property, requiring some standardization of each dimension in order to make things like Euclidean distance between vectors have meaning, but in this case we're good without any standardization.

For completeness, you can see the same charts for the other models below.  Because they have so many dimensions, the charts become unwieldy very quickly, which is why I presented the 50-dimension chart first.  I didn't study the rest of these other than to satisfy myself that the numerical distribution was comparable.

### glove.6B.300d

{% include model_boxplot.html id="glove.6B.300d" %}

Those 300 dimensions make this chart really wide, so you'll have to scroll to see all of them.  For the most part it's a smooth distribution consistent across all the dimensions, but there are some exceptions.  Dimension 9 and 34 and 244 and especially 276 have median values substantially above zero, while 150 and 200 are quite a bit below zero.  Whether or not this presents a problem in the results, we'll find out later.

### glove.42B.300d

{% include model_boxplot.html id="glove.42B.300d" %}

Here again we see a few dimensions stepping out of line with an interquartile range entirely above or below zero, though to my eye it seems more smooth and consistent than `glove.6B.300d` did.  That doesn't surprise me, since this model was trained with about 7x more data than `glove.6B.300d`.

### GoogleNews-vectors-negative300

{% include model_boxplot.html id="GoogleNews-vectors-negative300" %}

Look at how tight those interquartile-ranges are!  This is the only word2vec model I have, so I don't know if this distribution is characteristic of word2vec in general or if the Google News training corpus is responsible.  In any case, I like it.  96% of values are in the range (-0.45, 0.45).  Of course, a clean distribution doesn't automatically mean word2vec is superior; further testing is needed.

## Magnitude of Vectors

Given a 2 dimensional Cartesian plane, a line from the origin at `(0,0)` to `(5,3)` is how long?  We learned as children to apply the Pythagorean theorem to such problems, ie $$c = \sqrt{a^2 + b^2}$$.  That equation is specific to a two-dimensional coordinate, but it can be generalized to an arbitrary number of dimensions.  Let's say a vector with $$d$$ dimensions is represented as $$\mathbf{v}^d$$, where the value for the 1st dimension is $$\mathbf{v}_0$$, for the 2nd dimension is $$\mathbf{v}_1$$, etc.  The distance of that vector from the origin is called the magnitude of the vector, is written mathematically as $$\| \mathbf{v} \|$$, can be computed the same way we compute the 2D distance with Pythagoras' theorem:

$$ \| \mathbf{v}^d \| = \sqrt{ \sum_{i=0}^d \mathbf{v}_i^2 } $$

When a vector has more than three dimensions it's not possible for us to reason about it in terms of physical space, since we can only interact in three dimensional space, however the math is unaffected by this limitation.  I've computed the magnitude of every vector in every model, and then computed the statistical summary of these magnitudes.  Study the chart below and notice how each model differs:

{% include model_boxplot.html id="magnitudes" %}

Notice how wide the variation in magnitudes is.  All of the models have at least one vector whose magnitude is so close to zero, less than 0.05, that we can't even see it on this chart, and the biggest vector is in the teens, over 21 in the case of the word2vec model!  I think this will be a problem when measuring the distance between two vectors using the Pythagorean approach (called the Euclidean distance), because wide variations in magnitude mean there will be some vectors which are so much longer than the others that they will always be far away from any other vector, even if they have some sort of semantic relationship that should be preserved.

That brings me to the next section, about how to measure the distance between vectors.

# Measuring Distance Between Word Vectors

In the introduction I gave the example of computing some arithmetic operations on vectors, like `c = athens - greece + norway`, and made the claim that the resulting vector `c` will be closest to the vector for `oslo`.  But what does it mean 'closest'?

The most obvious meaning is the Euclidean distance.  This just requires applying the Pythagorean theorem to the difference between the two vectors.  So if $$ a = (1, 2, 3, 4) $$  and $$ b = (1, 5, 9, 8) $$, then the difference $$ a - b = (1 - 1, 2 - 5, 3 - 9, 4 - 8) = (0, -3, -6, -4) $$.  We know how to apply the Pythagorean theorem to compute the magnitude of that vector:

$$ \| a - b \| = \sqrt{ 0^2 + -3^2 + -6^2 + -4^2 } \approx 7.81 $$

So we can say that the distance between $$a$$ and $$b$$ is about $$ 7.81 $$.  If we had a bunch of vectors, and computed the distance from $$a$$ to all of them, if the one with the lowest distance was $$b$$, we'd say $$b$$ is closest to $$a$$.  Easy.

But remember in the previous section we noticed that the magnitudes of the word vectors in all of the models is widely variable.  This presents a problem for us, because it means that the distance between some words will be much higher than the distance to other words, and I am worried that this will impact the ability to capture the relationships between words.  I don't know this yet, so I'll compute Euclidean distance and see how it works, but there are other ways.

Both the word2vec and GloVe implementations do not bother with Euclidean distance when computing "closest" vectors.  Instead they use a metric called "[cosine similarity](https://en.wikipedia.org/wiki/Cosine_similarity)".  It's not a distance at all, it's the cosine of the angle between the two vectors.  It's hard to visualize the idea of an angle when the vectors have 50 or more dimensions, but again, the math doesn't change.  The cosine of the angle 0 degrees is 1.0, for any other angle it's less than that.  So if two vectors are along the same line, regardless of how much their magnitudes differ, their cosine similarity is 1.0, a perfect match.  Similarly, if two vectors are very close together but pointing in opposite directions, their cosine similarity is 0 or even less.

Cosine similarity is used in text mining often, so it's a proven technique.  It's computed easily enough, as the dot product of two vectors divided by the product of their magnitudes.  More formally:

$$ \cos( \theta ) = \frac { \mathbf{A} \cdot \mathbf{B} } { \| \mathbf{A} \| \|\mathbf{B} \| } $$

With Euclidean distance the lowest value means the closest match, while with cosine similarity the highest value means the closest match.

Which of these metrics is better?  To find out, I'm going to use the question data from the GloVe source tarball.  In the `eval/question-data` folder there are a number of text files, each with four words per line.  Each line is a solved analogy problem "A is to B as C is to D".  Here are some examples:

    athens greece baghdad iraq
    baghdad iraq bangkok thailand
    brother sister king queen
    bad worst simple simplest

For each of these lines, I will compute the analogy problem $$ d = b - a + c $$ where the answer is the word vector closest to $$d$$, where 'closest' will be defined by either Euclidean distance or cosine similarity.

<div id="tooltip" class="hidden">
</div>

<script langauge="javascript">
var vectorAnalysisData = null

var models = [ {% for model in site.data.models %}"{{model.name}}"{%if forloop.last != tru %},{%endif%}{%endfor%} ]

loadVectorAnalysisData( function(data) {
    vectorAnalysisData = data;

    for (var idx = 0; idx < models.length; idx++) {
      drawDimensionsWhiskerPlot(data, models[idx]);
    }

    drawMagnitudesWhiskerPlot(data, models);
  });
</script>

<!--</div>-->
