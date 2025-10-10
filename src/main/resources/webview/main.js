// Store all chart instances
const chartInstances = {
  heatmap: null,
  dailyHourHeatmap: null,
  overallHourlyChart: null
};

/**
 * Renders all charts with the provided data and theme information.
 * @param {string} payload - A JSON string containing theme colors and chart data.
 */
globalThis.renderCharts = function (payload) {
  try {
    const jsonPayload = JSON.parse(payload);
    const theme = jsonPayload.theme;

    // Render yearly activity heatmap
    if (jsonPayload.yearlyActivity) {
      renderYearlyActivityHeatmap(
          jsonPayload.yearlyActivity.data,
          jsonPayload.yearlyActivity.streaks,
          theme
      );
    }

    // Render daily hour heatmap
    if (jsonPayload.hourlyHeatmap) {
      renderDailyHourHeatmap(
          jsonPayload.hourlyHeatmap.data,
          theme
      );
    }

    // Render overall hourly distribution
    if (jsonPayload.overallHourly) {
      renderOverallHourlyChart(
          jsonPayload.overallHourly.data,
          theme
      );
    }
  } catch (e) {
    console.error("Failed to parse or render chart data:", e);
  }
};

/**
 * Renders the yearly activity contribution heatmap.
 * @param {Array<Object>} data - Array of data points
 * @param {Object} streaks - Streak information
 * @param {Object} theme - Theme colors
 */
function renderYearlyActivityHeatmap(data, streaks, theme) {
  disposeChart('heatmap');

  const chartDom = document.getElementById('heatmap');
  const chartTheme = theme.isDark ? 'dark' : 'default';
  chartInstances.heatmap = echarts.init(chartDom, chartTheme);

  const chartData = data.map(item => [item.date, item.seconds]);

  const endDate = new Date();
  const startDate = new Date();
  startDate.setFullYear(endDate.getFullYear() - 1);

  const option = {
    backgroundColor: 'transparent',
    title: [{
      top: 0,
      left: 'center',
      text: 'Yearly Coding Activity',
      textStyle: {
        color: theme.foreground
      }
    }, {
      bottom: 0,
      left: '10px',
      text: `Total Active Days: ${streaks.totalDays}`,
      textStyle: {color: theme.secondary, fontSize: 12}
    }, {
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
        {min: 1, max: 300, label: '< 5 min', color: '#00441b'},
        {min: 300, max: 900, label: '5–15 min', color: '#006d32'},
        {min: 900, max: 3600, label: '15 min–1 h', color: '#238b45'},
        {min: 3600, max: 10800, label: '1–3 h', color: '#41ab5d'},
        {min: 10800, max: 21600, label: '3–6 h', color: '#74c476'},
        {min: 21600, label: '> 6 h', color: '#bae4b3'}
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

  chartInstances.heatmap.setOption(option);
}

/**
 * Renders the daily hour distribution heatmap.
 * @param {Array<Object>} data - Array of data points
 * @param {Object} theme - Theme colors
 */
function renderDailyHourHeatmap(data, theme) {
  disposeChart('dailyHourHeatmap');

  const chartDom = document.getElementById('dailyHourHeatmap');
  const chartTheme = theme.isDark ? 'dark' : 'default';
  chartInstances.dailyHourHeatmap = echarts.init(chartDom, chartTheme);

  const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  const hours = Array.from({length: 24}, (_, i) => i);

  const chartData = data.map(item => [
    item.hour,
    item.dayOfWeek - 1,
    item.seconds
  ]);

  const maxSeconds = Math.max(...chartData.map(item => item[2]), 1);

  const option = {
    backgroundColor: 'transparent',
    title: {
      top: 0,
      left: 'center',
      text: 'Weekly Coding Activity by Hour',
      textStyle: {
        color: theme.foreground
      }
    },
    tooltip: {
      position: 'top',
      formatter: function (p) {
        const hour = p.data[0];
        const day = days[p.data[1]];
        const hours = (p.data[2] / 3600).toFixed(3);
        return `${day} ${hour}:00 - ${hours} hours`;
      }
    },
    grid: {
      top: 60,
      left: 100,
      right: 70,
      bottom: 20
    },
    xAxis: {
      type: 'category',
      data: hours,
      splitArea: {
        show: true
      },
      axisLabel: {
        color: theme.secondary,
        formatter: '{value}:00'
      }
    },
    yAxis: {
      type: 'category',
      data: days,
      splitArea: {
        show: true
      },
      axisLabel: {
        color: theme.secondary
      }
    },
    visualMap: {
      min: 0,
      max: maxSeconds,
      calculable: true,
      orient: 'vertical',
      right: 10,
      top: 'center',
      inRange: {
        color: ['#ebedf0', '#9be9a8', '#40c463', '#30a14e', '#216e39']
      },
      text: [
        (maxSeconds / 3600).toFixed(1) + ' h',
        '0.0 h'
      ],
      textStyle: {
        color: theme.secondary
      },
      formatter: function () {
        return '';
      }
    },
    series: [{
      type: 'heatmap',
      data: chartData,
      label: {
        show: false
      },
      emphasis: {
        itemStyle: {
          shadowBlur: 10,
          shadowColor: 'rgba(0, 0, 0, 0.5)'
        }
      }
    }]
  };

  chartInstances.dailyHourHeatmap.setOption(option);
}

/**
 * Renders the overall hourly distribution chart (24-hour coding pattern).
 * @param {Array<Object>} data - Array of data points with hour and seconds
 * @param {Object} theme - Theme colors
 */
function renderOverallHourlyChart(data, theme) {
  disposeChart('overallHourlyChart');

  const chartDom = document.getElementById('overallHourlyChart');
  const chartTheme = theme.isDark ? 'dark' : 'default';
  chartInstances.overallHourlyChart = echarts.init(chartDom, chartTheme);

  // Create 48 half-hour intervals
  const intervals = Array.from({length: 48}, (_, i) => {
    const h = Math.floor(i / 2);
    const m = (i % 2) * 30;
    return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}`;
  });

  const values = new Array(48).fill(0);

  // Fill in the actual data
  for (const item of data) {
    const index = item.hour * 2 + (item.minute === 30 ? 1 : 0);
    values[index] = item.seconds / 3600; // Convert to hours
  }

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: 'Daily Coding Distribution',
      left: 'center',
      top: 0,
      textStyle: {
        color: theme.foreground
      }
    },
    tooltip: {
      trigger: 'axis',
      formatter: function (params) {
        const timeLabel = params[0].axisValue;
        const value = params[0].value.toFixed(3);
        return `${timeLabel} - ${value} hours`;
      }
    },
    grid: {
      top: 60,
      left: 50,
      right: 30,
      bottom: 30
    },
    xAxis: {
      type: 'category',
      data: intervals,
      axisLabel: {
        color: theme.secondary,
        interval: 5
      },
      axisLine: {
        lineStyle: {
          color: theme.secondary
        }
      }
    },
    yAxis: {
      type: 'value',
      name: 'Hours',
      nameTextStyle: {
        color: theme.secondary
      },
      axisLabel: {
        color: theme.secondary,
        formatter: '{value} h'
      },
      splitLine: {
        lineStyle: {
          color: theme.isDark ? '#333' : '#e0e0e0'
        }
      }
    },
    series: [{
      data: values,
      type: 'line',
      smooth: true,
      showSymbol: false,
      smoothMonotone: 'x',
      sampling: 'lttb',
      areaStyle: {
        opacity: 0.3,
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 0,
          y2: 1,
          colorStops: [{
            offset: 0, color: 'rgba(64, 196, 99, 0.3)'
          }, {
            offset: 1, color: 'rgba(64, 196, 99, 0.05)'
          }]
        }
      },
      lineStyle: {
        width: 2.5,
        color: '#40c463'
      },
      emphasis: {
        focus: 'series',
        lineStyle: {
          width: 3
        }
      }
    }]
  };

  chartInstances.overallHourlyChart.setOption(option);
}

/**
 * Disposes a chart instance if it exists.
 * @param {string} chartKey - The key of the chart in chartInstances.
 */
function disposeChart(chartKey) {
  if (chartInstances[chartKey]) {
    chartInstances[chartKey].dispose();
    chartInstances[chartKey] = null;
  }
}

/**
 * Resize all active charts when window is resized.
 */
window.addEventListener('resize', function () {
  for (const chart of Object.values(chartInstances)) {
    if (chart) {
      chart.resize();
    }
  }
});
