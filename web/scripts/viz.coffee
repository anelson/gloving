---

---

createBoxPlot = (parentSelector, data, singleDimWidth = 6, singleDimMargin = {top: 10, right: 1, bottom: 10, left: 1}, labels = null) ->
  margin = {top: 10, right: 10, bottom: 10, left: 10}

  height = 600 - margin.top - margin.bottom

  chart = d3.whiskerPlot()
    .height(height)
    .singleDim.width(singleDimWidth)
    .singleDim.margin(singleDimMargin)
    .tickCount(20)

  chart = chart.labels(labels) if labels != null

  width = chart.computeWidth(data)

  svg = d3.select(parentSelector).append("svg")
      .attr("class", "box")
      .attr("width", width + margin.left + margin.right)
      .attr("height", height + margin.bottom + margin.top)
      .append("g")
        .attr("transform", "translate(#{margin.left}, #{margin.top})")
  svg.call(chart, data)

  chart

updateBoxPlot = (chart, parentSelector, data) ->
  svg = d3.select(parentSelector).select("svg").select("g")
  svg.call(chart, data)

# Given data from the raw vector analysis JSON, transforms it
# so the min/max range covers the the range returned by the rangeFunc
filterDataRange = (data, rangeFunc) ->
  dprime = _.cloneDeep(data)
  dprime = _.map(dprime, (d) ->
    d.range = rangeFunc(d)

    d
  )


  dprime

# A range func passed to filterDataRange, which uses the entire range of the dataset
allValuesRange = (data) ->
  [data.min, data.max]

ninetysixPercentRange = (data) ->
  [data.percentiles['2'], data.percentiles['98']]

iqr1point5Range = (data) ->
  median = data.percentiles['50']
  iqr = data.percentiles['75'] - data.percentiles['25']

  [median - iqr, median + iqr]

chooseFilter = (name) ->
  switch name
      when "100" then allValuesRange
      when "96" then ninetysixPercentRange
      when "iqr15" then iqr1point5Range
      else allValuesRange

window.loadVectorAnalysisData = (callback) ->
  d3.json("vector-analysis.json", (error, json) ->
    throw error if error

    vectorAnalysis = json

    callback(vectorAnalysis)
  )

window.drawDimensionsWhiskerPlot = (vectorAnalysis, name) ->
  source = vectorAnalysis[name].dimensionStats
  idName = name.replace(/\./g, "_")

  boxPlotChart = createBoxPlot("##{idName}_viz", filterDataRange(source, allValuesRange))

  d3.select("##{idName}_includePercentage").on("change", () ->
    filter = chooseFilter(this.value)

    updateBoxPlot(boxPlotChart, "##{idName}_viz", filterDataRange(source, filter))
  )

window.drawMagnitudesWhiskerPlot = (vectorAnalysis, names) ->
  # Gather the magnitude stats for the models specified in names, and draw them in a box plot,
  # one box per model

  # First filter vectorAnalysis for just the models specified in the names parameter
  models = _.map(names, (x) -> vectorAnalysis[x])

  # Throw away most of the data for each model; we care only about the normStats
  stats = _.map(models, (x) -> x.normStats)

  # Use the names as the labels
  labels = names

  boxPlotChart = createBoxPlot("#magnitudes_viz",
    filterDataRange(stats, allValuesRange),
    100,
    {top: 10, right: 10, bottom: 10, left: 10}
    (d, i) -> labels[i])

  d3.select("#magnitudes_includePercentage").on("change", () ->
    filter = chooseFilter(this.value)

    updateBoxPlot(boxPlotChart, "#magnitudes_viz", filterDataRange(stats, filter))
  )


source = null
boxPlotChart = null
vectorAnalysis = null


