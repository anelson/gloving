---

---

# Renders a box and whiskers plot with each data element consisting of an object with the following properties:
#  - min
#  - max
#  - mean
#  - stdev
d3.whiskerPlot = () ->
  axisWidth = 23
  height = 500
  tickCount = 20
  duration = 1000
  tooltipSelector = "#tooltip"

  # Size and margins for each individual box and whisker, one of which corresponds
  # to each dimension of the data
  singleDim =
    margin:
      top: 10
      right: 1
      bottom: 10
      left: 1

  singleDim.width = 8 - singleDim.margin.left - singleDim.margin.right

  render = (svg, data) ->
    singleDimTotalWidth = computeSingleDimTotalWidth()
    width = computeWidth(data)

    min = _.min(data, (x) -> x.min).min
    max = _.max(data, (x) -> x.max).max

    scale = d3.scale.linear().domain([min, max]).range([height, 0]).nice(tickCount)
    axis = d3.svg.axis().scale(scale).ticks(tickCount)

    singleDimChart = d3.box()
      .width(singleDim.width)
      .height(height - singleDim.margin.top - singleDim.margin.bottom)
      .yScale(scale)
      .duration(duration)

    # Create the initial elements of the graph that will not change between renderings
    # TODO: This data([0]) thing seems like a hack but I can't find another way to do this and yet still
    # be able to call render multiple times with different values and transition between them
    #
    # From https://groups.google.com/forum/#!topic/d3-js/Rlv0O8xsFhs this seems to be idiomatic, according to
    # Mike Bostock himself
    enter = svg.selectAll("*") # Note using '*' here obviously assumes that the selector 'svg' has no elements when the chart is first created
      .data([null])
      .enter()

    # Create the tooltip
    d3.selectAll(tooltipSelector)
      .data([null])
      .enter().append("div")
      .attr("id", "tooltip")
      .attr("class", "hidden")
      .append("p")
        .append("span")
          .attr("id", "value")

    # Render the Y axis at both the far left and far right side of the chart
    enter.append("g")
        .attr("class", "yaxis left")
        .attr("transform", "translate(#{axisWidth}, 0)")
        .call(axis.orient("left"))

    enter.append("g")
        .attr("class", "yaxis right")
        .attr("transform", "translate(#{width - axisWidth}, 0)")
        .call(axis.orient("right"))

    # Update the axes with a transition
    svg.select("g.yaxis.left")
      .transition()
      .duration(duration)
      .call(axis.orient("left"))

    svg.select("g.yaxis.right")
      .transition()
      .duration(duration)
      .call(axis.orient("right"))

    # Create the group that will hold the grid lines
    enter.insert("g", "g.datapoint")
      .attr("class", "gridlines")

    # Draw grid lines
    gridlines = svg.select("g.gridlines")
      .selectAll("line.horizontalGrid")
      .data(scale.ticks(tickCount))

    gridlines
        .enter().append("line")
          .attr(
            class: "horizontalGrid"
            x1: axisWidth   # start the gridlines to the right of the axis that's on the left side
            x2: width - axisWidth # and end them at the left of the axis that's on the right side
            y1: (d) -> scale(d)
            y2: (d) -> scale(d)
            fill: "none"
            #"shape-rendering": "crispEdges"
            stroke: "black"
            "stroke-width": "1px"
          )

    gridlines.transition()
      .duration(duration)
      .attr("y1", (d) -> scale(d))
      .attr("y2", (d) -> scale(d))

    gridlines.exit().remove()

    # Draw the individual boxes and whiskers for each data point
    datapoints = svg.selectAll("g.datapoint")
      .data(data)

    datapoints.enter().insert("g")
        .attr("class", "datapoint")
        .attr("transform", (d, i) -> "translate(#{axisWidth + singleDimTotalWidth * i}, 0)")
        .call(singleDimChart)
        .on("mouseover", showTooltip)
        .on("mouseout", hideTooltip)

    datapoints.transition()
      .duration(duration)
      .call(singleDimChart)

  getTooltipText = (d, i) ->
    fmt = d3.format(".5g")

    "
    <ul>
      <li>mean: #{fmt(d.mean)}</li>
      <li>stdev: #{fmt(d.stdev)}</li>
      <li>median: #{fmt(d.percentiles['50'])}</li>
      <li>50% of values are between [#{fmt(d.percentiles['25'])} and #{fmt(d.percentiles['75'])}]</li>
      <li>82% of values are between [#{fmt(d.percentiles['9'])} and #{fmt(d.percentiles['91'])}]</li>
      <li>96% of values are between [#{fmt(d.percentiles['2'])} and #{fmt(d.percentiles['98'])}]</li>
      <li>100% of values are between [#{fmt(d.min)} and #{fmt(d.max)}]</li>
    </ul>"

  showTooltip = (d, i) ->
    d3.select(this).select("rect.datapointbg").classed("cell-hover", true)
    tooltip = d3.select("#tooltip")
      .style("left", d3.event.pageX+10 + "px")
      .style("top", d3.event.pageY-10 + "px")

    renderTooltip(tooltip, d, i)

    d3.select("#tooltip").classed("hidden", false)

  renderTooltip = (tooltip, d, i) ->
    tooltip.html("
      <div class='tooltip-title'>Dimension: #{i} (0-based)</div>
      <div class='tooltip-stats'>#{getTooltipText(d, i)}</div>")

  hideTooltip = (d) ->
    d3.select(this).select("rect.datapointbg").classed("cell-hover", false)
    d3.select("#tooltip").classed("hidden", true)

  computeWidth = (data) -> computeSingleDimTotalWidth() * data.length + 2 * axisWidth

  computeSingleDimTotalWidth = () -> singleDim.width + singleDim.margin.left + singleDim.margin.right

  render.height = (x) ->
    if (!arguments.length)
      height
    else
      height = x
      render

  render.duration = (x) ->
    if (!arguments.length)
      duration
    else
      duration = x
      render

  render.tooltipSelector = (x) ->
    if (!arguments.length)
      tooltipSelector
    else
      tooltipSelector = x
      render

  render.tickCount = (x) ->
    if (!arguments.length)
      tickCount
    else
      tickCount = x
      render

  render.singleDim = (x) ->
    if (!arguments.length)
      singleDim
    else
      singleDim = x
      render

  render.singleDim.margin = (x) ->
    if (!arguments.length)
      singleDim.margin
    else
      singleDim.margin = x
      render

  render.singleDim.width = (x) ->
    if (!arguments.length)
      singleDim.width
    else
      singleDim.width = x
      render

  render.computeWidth = computeWidth


  render


# Inspired by http://informationandvisualization.de/blog/box-plot
d3.box = () ->
  width = 1
  height = 1
  yScale = null
  value = Number
  duration = 1000

  # Renders one box whiskers plot for a single data element, where g is a selector describing the
  # parent
  #  - min
  #  - max
  #  - mean
  #  - stdev
  box = (g) ->
    g.each (d, i) ->
      dimension = i

      g = d3.select(this)
      n = d.length

      whiskerData = [d.min, d.max]

      # background rectangle for highlighting and such
      bg = g.selectAll("rect.datapointbg")
        .data([null])

      bg.enter().append("rect")
        .attr("class", "datapointbg")
        .attr("x", 0)
        .attr("y", yScale(d.max))
        .attr("width", width)
        .attr("height", yScale(d.min) - yScale(d.max))

      bg.transition()
        .duration(duration)
        .attr("y", yScale(d.max))
        .attr("height", yScale(d.min) - yScale(d.max))

      # center line: the vertical line spanning the whiskers.
      center = g.selectAll("line.center")
          .data([ whiskerData ])

      center.enter().insert("line", "rect")
          .attr("class", "center")
          .attr("x1", width / 2)
          .attr("y1", (d) -> yScale(d[0]) )
          .attr("x2", width / 2)
          .attr("y2",  (d) -> yScale(d[1]) )

      center.transition()
        .duration(duration)
          .attr("y1", (d) -> yScale(d[0]) )
          .attr("y2",  (d) -> yScale(d[1]) )

      # IQR box
      box = g.selectAll("rect.box")
          .data([d])

      box.enter().append("rect")
          .attr("class", "box")
          .attr("x", 0)
          .attr("y", (d) -> yScale(d.percentiles['75']))
          .attr("width", width)
          .attr("height", (d) -> yScale(d.percentiles['25']) - yScale(d.percentiles['75']))

      box.transition()
        .duration(duration)
        .attr("y", (d) -> yScale(d.percentiles['75']))
        .attr("height", (d) -> yScale(d.percentiles['25']) - yScale(d.percentiles['75']))

      # Median line
      medianLine = g.selectAll("line.median")
          .data([d.percentiles['50']])

      medianLine.enter().append("line")
          .attr("class", "median")
          .attr("x1", 0)
          .attr("y1", yScale)
          .attr("x2", width)
          .attr("y2", yScale)

      medianLine.transition()
        .duration(duration)
          .attr("y1", yScale)
          .attr("y2", yScale)

      # Whiskers
      whisker = g.selectAll("line.whisker")
          .data(whiskerData)

      whisker.enter().insert("line", "circle, text")
          .attr("class", "whisker")
          .attr("x1", 0)
          .attr("y1", yScale)
          .attr("x2", width)
          .attr("y2", yScale)

      whisker.transition()
        .duration(duration)
        .attr("y1", yScale)
        .attr("y2", yScale)


  box.width = (x) ->
    if (!arguments.length)
      width
    else
      width = x
      box

  box.height = (x) ->
    if (!arguments.length)
      height
    else
      height = x
      box

  box.yScale = (x) ->
    if (!arguments.length)
      yScale
    else
      yScale = x
      box

  box.duration = (x) ->
    if (!arguments.length)
      duration
    else
      duration = x
      box

  box.value = (x) ->
    if (!arguments.length)
      value
    else
      value = x
      box

  box
