---

---
d3.json("vector-analysis.json", (error, json) ->
  throw error if error

  source = json["GoogleNews-vectors-negative300"].unprocessed
  data = source.dimensionStats

  margin = {top: 10, right: 10, bottom: 10, left: 10}

  height = 500 - margin.top - margin.bottom
  chart = d3.whiskerPlot()
    .margin(margin)
    .height(height)
    .singleDim.width(6)
    .singleDim.margin({top: 10, right: 1, bottom: 10, left: 1})
    .tickCount(20)

  chart(d3.select(".viz"), data)
)