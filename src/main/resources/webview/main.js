const myChart = echarts.init(document.getElementById('chart1'));

const option = {
  title: {
    text: 'Code Time Statistics'
  },
  tooltip: {},
  legend: {
    data: ['Time']
  },
  xAxis: {
    data: ["Category1", "Category2", "Category3", "Category4", "Category5", "Category6"]
  },
  yAxis: {},
  series: [{
    name: 'Time',
    type: 'bar',
    data: [5, 20, 36, 10, 10, 20]
  }]
};

myChart.setOption(option);