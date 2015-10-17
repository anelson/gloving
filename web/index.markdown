---
layout: default
---

<!--<script src="http://underscorejs.org/underscore-min.js"></script>-->
<script src="https://cdnjs.cloudflare.com/ajax/libs/lodash.js/3.10.1/lodash.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/d3/3.5.6/d3.min.js" charset="utf-8"></script>
<script src="scripts/whisker-plot.js"></script>
<script src="scripts/viz.js"></script>

<div class="home">

  <h1 class="page-heading">Exploring pre-trained word vectors</h1>

  Visualize:
  <select id="includePercentage">
  <option value="100" selected="true">100% of data</option>
  <option value="96">96% of data, excluding outliers at each end</option>
  <option value="iqr15">+/- 1.5xIQR</option>
  </select>

  <div class="viz">
  </div>

  <div id="tooltip">
  </div>

  <p class="rss-subscribe">subscribe <a href="{{ "/feed.xml" | prepend: site.baseurl }}">via RSS</a></p>

</div>
