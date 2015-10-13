---

---

data = [ {
        "max" : 2.96875,
        "min" : -2.34375,
        "mean" : -0.000225727391761843,
        "stdev" : 0.12262429011140785,
        "variance" : 0.015036716525326718
      }, {
        "max" : 1.265625,
        "min" : -2.375,
        "mean" : -0.0010130284912407358,
        "stdev" : 0.12312609994739053,
        "variance" : 0.015160036488254801
      }, {
        "max" : 2.234375,
        "min" : -2.1875,
        "mean" : -0.0104770563492062,
        "stdev" : 0.12132436077936319,
        "variance" : 0.014719600518521081
      }, {
        "max" : 2.265625,
        "min" : -2.09375,
        "mean" : 0.031463274962860624,
        "stdev" : 0.1300255471585308,
        "variance" : 0.01690664291387531
      }, {
        "max" : 2.234375,
        "min" : -2.046875,
        "mean" : 0.0019490353207848212,
        "stdev" : 0.12175547863699838,
        "variance" : 0.014824396578124571
      }]

d3.json("vector-analysis.json", (error, json) ->
  throw error if error

  source = json["GoogleNews-vectors-negative300"].unprocessed
  data = source.dimensionStats
  n = source.dimensionality

  margin = {top: 10, right: 1, bottom: 20, left: 1}
  width = 8 - margin.left - margin.right
  widthWithMargin = width + margin.left + margin.right
  height = 500 - margin.top - margin.bottom
  axisWidth = 23 + margin.left
  tickCount = 20
  graphWidth = widthWithMargin * n + axisWidth

  min = Infinity
  max = -Infinity

  chart = d3.box()
    .width(width)
    .height(height)

  #data = [];
  min = _.min(data, (x) -> x.min).min
  max = _.max(data, (x) -> x.max).max

  chart.domain([min, max]);
  scale = d3.scale.linear().domain([min, max]).range([height, 0]).nice(tickCount)
  axis = d3.svg.axis().scale(scale).orient("left").ticks(tickCount)

  svg = d3.select(".viz").select("svg")
      .attr("class", "box")
      .attr("width", graphWidth)
      .attr("height", height + margin.bottom + margin.top)

  svg.append("g")
      .attr("class", "y axis")
      .attr("transform", "translate(#{axisWidth - margin.left}, #{margin.top})")
      .call(axis)

  svg.selectAll("g.datapoint")
    .data(data)
    .enter().append("g")
      .attr("class", "datapoint")
      .attr("transform", (d, i) -> "translate(" + (axisWidth + widthWithMargin * i) + "," + margin.top + ")")
      .call((g) ->
        g.append("title")
          .text( (d, i) ->
            "Dimension #{i}:\n
            - min: #{d.min}\n
            - max: #{d.max}\n
            - mean: #{d.mean}\n
            - stdev: #{d.stdev}"
          )
      )
      .call(chart)

  # Draw grid lines
  svg.selectAll("line.horizontalGrid").data(scale.ticks(tickCount))
    .enter().append("line")
      .attr(
        class: "horizontalGrid"
        x1: margin.right
        x2: graphWidth
        y1: (d) -> scale(d)
        y2: (d) -> scale(d)
        fill: "none"
        "shape-rendering": "crispEdges"
        stroke: "black"
        "stroke-width": "1px"
      )
)
