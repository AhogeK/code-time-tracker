window.renderCharts = function (jsonData) {
  try {
    const data = JSON.parse(jsonData);
    const chartDom = document.getElementById('chart-container');
    const myChart = echarts.init(chartDom);
    const option = {
      title: {
        text: data.title || 'Coding Statistics'
      },
      tooltip: {
        trigger: 'axis',
        formatter: '{b}: {c} minutes'
      },
      xAxis: {
        type: 'category',
        data: data.categories || []
      },
      yAxis: {
        type: 'value',
        name: 'Minutes'
      },
      series: [{
        data: data.values || [],
        type: 'bar',
        showBackground: true,
        backgroundStyle: {
          color: 'rgba(180, 180, 180, 0.2)'
        }
      }]
    };
    myChart.setOption(option)
    window.addEventListener('resize', function () {
      myChart.resize();
    });
  } catch (error) {
    console.error("Failed to render chart:", error);
    // display error message on the page for easier debugging
    document.body.innerHTML = `<div style="color: red;">Error rendering chart: ${error}</div>`;
  }
}