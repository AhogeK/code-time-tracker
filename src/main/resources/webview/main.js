// A global variable to hold the chart instance
let heatmapChart = null;

/**
 * Renders all charts with the provided data and theme information.
 * @param {string} payload - A JSON string containing theme colors and chart data.
 */
globalThis.renderCharts = function (payload) {
  try {
    const jsonPayload = JSON.parse(payload);
    renderHeatmap(jsonPayload.data, jsonPayload.theme);
  } catch (e) {
    console.error("Failed to parse or render chart data:", e);
  }
};

/**
 * Renders the contribution heatmap.
 * @param {Array<Object>} data - Array of data points, e.g., [{date: 'YYYY-MM-DD', seconds: 120}, ...]
 * @param {Object} theme - Object containing theme colors, e.g., {foreground: '#rrggbb', secondary: '#rrggbb'}
 */
function renderHeatmap(data, theme) {
  // If the chart is already initialized, just dispose of it to start fresh
  if (heatmapChart) {
    heatmapChart.dispose();
  }

  const chartDom = document.getElementById('heatmap');
  const chartTheme = theme.isDark ? 'dark' : 'default';
  heatmapChart = echarts.init(chartDom, chartTheme);

  // Transform the incoming data into the format ECharts expects for the heatmap series: [['YYYY-MM-DD', value], ...]
  const chartData = data.map(item => [item.date, item.seconds]);

  // Get the start and end dates for the calendar range
  const year = new Date().getFullYear();

  const option = {
    backgroundColor: 'transparent',
    // Add a title to the chart
    title: {
      top: 0,
      left: 'center',
      text: 'Yearly Coding Activity',
      textStyle: {
        color: theme.foreground // Use dynamic color
      }
    },
    tooltip: {
      formatter: function (p) {
        const hours = (p.data[1] / 3600).toFixed(2);
        return `${p.data[0]}: ${hours} hours`;
      }
    },
    visualMap: {
      top: 40,
      min: 0,
      max: 10000,
      type: 'piecewise',
      orient: 'horizontal',
      left: 'center',
      pieces: [
        {min: 1, max: 900, label: '< 15min', color: '#0e4429'},
        {min: 900, max: 3600, label: '15min - 1h', color: '#006d32'},
        {min: 3600, max: 10800, label: '1h - 3h', color: '#26a641'},
        {min: 10800, label: '> 3h', color: '#39d353'}
      ],
      textStyle: {
        color: theme.secondary
      }
    },
    calendar: {
      top: 100,
      left: 30,
      right: 30,
      cellSize: ['auto', 13],
      range: year.toString(),
      dayLabel: {
        color: theme.secondary
      },
      monthLabel: {
        color: theme.secondary
      },
      yearLabel: {show: false},
    },
    series: {
      type: 'heatmap',
      coordinateSystem: 'calendar',
      data: chartData
    }
  };

  heatmapChart.setOption(option);
}

// Make the chart responsive to window resizing
window.addEventListener('resize', function () {
  if (heatmapChart) {
    heatmapChart.resize();
  }
});