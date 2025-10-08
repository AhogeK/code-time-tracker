// A global variable to hold the chart instance
let heatmapChart = null;

/**
 * Renders all charts with the provided data and theme information.
 * @param {string} payload - A JSON string containing theme colors and chart data.
 */
globalThis.renderCharts = function (payload) {
  try {
    const jsonPayload = JSON.parse(payload);
    // Pass the new streaks data to the heatmap function
    renderHeatmap(jsonPayload.data, jsonPayload.theme, jsonPayload.streaks);
  } catch (e) {
    console.error("Failed to parse or render chart data:", e);
  }
};

/**
 * Renders the contribution heatmap.
 * @param {Array<Object>} data - Array of data points, e.g., [{date: 'YYYY-MM-DD', seconds: 120}, ...]
 * @param {Object} theme - Object containing theme colors, e.g., {foreground: '#rrggbb', secondary: '#rrggbb'}
 * @param {Object} streaks - Object containing streak data, e.g., {current: 5, max: 10, totalDays: 150}
 */
function renderHeatmap(data, theme, streaks) {
  // If the chart is already initialized, just dispose of it to start fresh
  if (heatmapChart) {
    heatmapChart.dispose();
  }

  const chartDom = document.getElementById('heatmap');
  const chartTheme = theme.isDark ? 'dark' : 'default';
  heatmapChart = echarts.init(chartDom, chartTheme);

  const chartData = data.map(item => [item.date, item.seconds]);

  // Set the date range to the last year
  const endDate = new Date();
  const startDate = new Date();
  startDate.setFullYear(endDate.getFullYear() - 1);

  const option = {
    backgroundColor: 'transparent',
    // Use an array to manage multiple titles (main title and footer text)
    title: [{
      top: 0,
      left: 'center',
      text: 'Yearly Coding Activity',
      textStyle: {
        color: theme.foreground // Use dynamic color
      }
    }, {
      bottom: 0,
      left: '10px',
      text: `Total Active Days: ${streaks.totalDays}`,
      textStyle: {color: theme.secondary, fontSize: 12}
    },
      {
        bottom: 0,
        right: '10px',
        text: `Max Streak: ${streaks.max} days / Current Streak: ${streaks.current} days`,
        textStyle: {color: theme.secondary, fontSize: 12}
      }],
    tooltip: {
      formatter: function (p) {
        const hours = (p.data[1] / 3600).toFixed(2);
        return `${p.data[0]}: ${hours} hours`;
      }
    },
    visualMap: {
      top: 40,
      min: 0,
      max: 21600,
      type: 'piecewise',
      orient: 'horizontal',
      left: 'center',
      pieces: [
        {min: 1, max: 300, label: '< 5 min', color: '#00441b'},  // 1–5 min
        {min: 300, max: 900, label: '5–15 min', color: '#006d32'},  // 5–15 min
        {min: 900, max: 3600, label: '15 min–1 h', color: '#238b45'},  // 15 min–1 h
        {min: 3600, max: 10800, label: '1–3 h', color: '#41ab5d'},  // 1–3 h
        {min: 10800, max: 21600, label: '3–6 h', color: '#74c476'},  // 3–6 h
        {min: 21600, label: '> 6 h', color: '#bae4b3'}   // over 6 h
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
      range: [startDate.toISOString().slice(0, 10), endDate.toISOString().slice(0, 10)],
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