---

---

# Inspired by http://informationandvisualization.de/blog/box-plot
d3.box = () ->
  width = 1
  height = 1
  yScale = null
  value = Number
  tickFormat = null

  # Renders a box and whiskers plot with each data element consisting of an object with the following properties:
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

      # Tick format
      format = tickFormat || yScale.tickFormat(5, ".5g")

      # center line: the vertical line spanning the whiskers.
      center = g.selectAll("line.center")
          .data(if whiskerData then [whiskerData] else [])

      center.enter().insert("line", "rect")
          .attr("class", "center")
          .attr("x1", width / 2)
          .attr("y1", (d) -> yScale(d[0]) )
          .attr("x2", width / 2)
          .attr("y2",  (d) -> yScale(d[1]) )

      # IQR box (though in this case it's actually std dev box)
      box = g.selectAll("rect.box")
          .data([d])

      box.enter().append("rect")
          .attr("class", "box")
          .attr("x", 0)
          .attr("y", (d) -> yScale(d.mean + d.stdev) )
          .attr("width", width)
          .attr("height", (d) -> yScale(d.mean - d.stdev) - yScale(d.mean + d.stdev) )

      # Median line (although in this case it's mean line)
      medianLine = g.selectAll("line.median")
          .data([d.mean])

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

  box.tickFormat = (x) ->
    if (!arguments.length)
      tickFormat
    else
      tickFormat = x
      box

  box.duration = (x) ->
    if (!arguments.length)
      duration
    else
      duration = x
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
