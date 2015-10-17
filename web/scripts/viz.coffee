---

---

createBoxPlot = (parentSelector, data) ->
  margin = {top: 10, right: 10, bottom: 10, left: 10}

  height = 600 - margin.top - margin.bottom

  chart = d3.whiskerPlot()
    .height(height)
    .singleDim.width(6)
    .singleDim.margin({top: 10, right: 1, bottom: 10, left: 1})
    .tickCount(20)
  width = chart.computeWidth(data.dimensionStats)

  svg = d3.select(parentSelector).append("svg")
      .attr("class", "box")
      .attr("width", width + margin.left + margin.right)
      .attr("height", height + margin.bottom + margin.top)
      .append("g")
        .attr("transform", "translate(#{margin.left}, #{margin.top})")
  svg.call(chart, data.dimensionStats)

  chart

updateBoxPlot = (chart, parentSelector, data) ->
  svg = d3.select(parentSelector).select("svg").select("g")
  svg.call(chart, data.dimensionStats)

# Given data from the raw vector analysis JSON, transforms it
# so the min/max range covers the the range returned by the rangeFunc
filterDataRange = (data, rangeFunc) ->
  dprime = _.cloneDeep(data)
  dprime.dimensionStats = _.map(dprime.dimensionStats, (d) ->
    [min, max] = rangeFunc(d)

    d.min = min
    d.max = max

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


source = null
boxPlotChart = null

d3.json("vector-analysis.json", (error, json) ->
  throw error if error

  source = json["GoogleNews-vectors-negative300"]

  boxPlotChart = createBoxPlot(".viz", filterDataRange(source, allValuesRange))

  d3.select("#includePercentage").on("change", () ->
    filter = switch this.value
      when "100" then allValuesRange
      when "96" then ninetysixPercentRange
      when "iqr15" then iqr1point5Range
      else allValuesRange

    updateBoxPlot(boxPlotChart, ".viz", filterDataRange(source, filter))
  )
)
