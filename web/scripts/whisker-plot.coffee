---

---

# Renders a box and whiskers plot with each data element consisting of an object with the following properties:
#  - min
#  - max
#  - mean
#  - stdev
d3.whiskerPlot = () ->
  margin = { top: 0, right: 0, left: 0, bottom: 0 }
  height = 500
  tickCount = 20

  # Size and margins for each individual box and whisker, one of which corresponds
  # to each dimension of the data
  singleDim =
    margin:
      top: 10
      right: 1
      bottom: 10
      left: 1

  singleDim.width = 8 - singleDim.margin.left - singleDim.margin.right

  render = (parentSelector, data) ->
    n = data.length

    singleDimTotalWidth = singleDim.width + singleDim.margin.left + singleDim.margin.right
    axisWidth = 23
    width = singleDimTotalWidth * n + 2 * axisWidth

    min = _.min(data, (x) -> x.min).min
    max = _.max(data, (x) -> x.max).max

    scale = d3.scale.linear().domain([min, max]).range([height, 0]).nice(tickCount)
    axis = d3.svg.axis().scale(scale).orient("left").ticks(tickCount)

    singleDimChart = d3.box()
      .width(singleDim.width)
      .height(height - singleDim.margin.top - singleDim.margin.bottom)
      .yScale(scale)

    svg = parentSelector.append("svg")
        .attr("class", "box")
        .attr("width", width + margin.left + margin.right)
        .attr("height", height + margin.bottom + margin.top)
        .append("g")
          .attr("transform", "translate(#{margin.left}, #{margin.top})")

    # Create the tooltip
    parentSelector.append("div")
      .attr("id", "tooltip")
      .attr("class", "hidden")
      .append("p")
        .append("span")
          .attr("id", "value")

    # Render the Y axis at both the far left and far right side of the chart
    svg.append("g")
        .attr("class", "y axis")
        .attr("transform", "translate(#{axisWidth}, 0)")
        .call(axis)

    svg.append("g")
        .attr("class", "y axis")
        .attr("transform", "translate(#{width - axisWidth}, 0)")
        .call(axis.orient("right"))

    # Draw grid lines
    svg.insert("g", "g.datapoint")
      .attr("class", "gridlines")
      .selectAll("line.horizontalGrid")
      .data(scale.ticks(tickCount))
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

    # Draw the individual boxes and whiskers for each data point
    svg.selectAll("g.datapoint")
      .data(data)
      .enter().insert("g")
        .attr("class", "datapoint")
        .attr("transform", (d, i) -> "translate(#{axisWidth + singleDimTotalWidth * i}, 0)")
        .call(singleDimChart)
        .on("mouseover", showTooltip)
        .on("mouseout", hideTooltip)

  getTooltipText = (d, i) ->
    "Dimension #{i}:\n
      - min: #{d.min}\n
      - max: #{d.max}\n
      - mean: #{d.mean}\n
      - stdev: #{d.stdev}\n
      - median: #{d.median}\n
      - q1: #{d.q1}\n
      - q3: #{d.q3}\n
      - iqr: #{d.q3 - d.q1}"

  showTooltip = (d, i) ->
    d3.select(this).select("rect.datapointbg").classed("cell-hover", true)
    d3.select("#tooltip")
      .style("left", d3.event.pageX+10 + "px")
      .style("top", d3.event.pageY-10 + "px")
      .select("#value")
      .text(getTooltipText(d, i))

    d3.select("#tooltip").classed("hidden", false)

  hideTooltip = (d) ->
    d3.select(this).select("rect.datapointbg").classed("cell-hover", false)
    d3.select("#tooltip").classed("hidden", true)

  render.height = (x) ->
    if (!arguments.length)
      height
    else
      height = x
      render

  render.margin = (x) ->
    if (!arguments.length)
      margin
    else
      margin = x
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

  render


# Inspired by http://informationandvisualization.de/blog/box-plot
d3.box = () ->
  width = 1
  height = 1
  yScale = null
  value = Number

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
      rect = g.append("rect")
        .attr("class", "datapointbg")
        .attr("x", 0)
        .attr("y", yScale(d.max))
        .attr("width", width)
        .attr("height", yScale(d.min) - yScale(d.max))

      # center line: the vertical line spanning the whiskers.
      center = g.selectAll("line.center")
          .data([whiskerData])

      center.enter().insert("line", "rect")
          .attr("class", "center")
          .attr("x1", width / 2)
          .attr("y1", (d) -> yScale(d[0]) )
          .attr("x2", width / 2)
          .attr("y2",  (d) -> yScale(d[1]) )

      # IQR box
      box = g.selectAll("rect.box")
          .data([d])

      box.enter().append("rect")
          .attr("class", "box")
          .attr("x", 0)
          .attr("y", (d) -> yScale(d.q3))
          .attr("width", width)
          .attr("height", (d) -> yScale(d.q1) - yScale(d.q3))

      # Median line
      medianLine = g.selectAll("line.median")
          .data([d.median])

      medianLine.enter().append("line")
          .attr("class", "median")
          .attr("x1", 0)
          .attr("y1", yScale)
          .attr("x2", width)
          .attr("y2", yScale)

      # Whiskers
      whisker = g.selectAll("line.whisker")
          .data(whiskerData || [])

      whisker.enter().insert("line", "circle, text")
          .attr("class", "whisker")
          .attr("x1", 0)
          .attr("y1", yScale)
          .attr("x2", width)
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

  box.value = (x) ->
    if (!arguments.length)
      value
    else
      value = x
      box

  box
