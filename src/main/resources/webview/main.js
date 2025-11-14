/**
 * Store all chart instances
 */
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

    applyScrollbarTheme(theme);

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
          jsonPayload.overallHourly.totalDays,
          theme
      );
    }
  } catch (e) {
    console.error("Failed to parse or render chart data:", e);
  }
};

/**
 * Applies scrollbar theme based on the current theme colors.
 * @param {Object} theme - Theme colors
 */
function applyScrollbarTheme(theme) {
  const root = document.documentElement;

  if (theme.isDark) {
    root.style.setProperty('--scrollbar-thumb-color', 'rgba(255, 255, 255, 0.2)');
    root.style.setProperty('--scrollbar-thumb-hover-color', 'rgba(255, 255, 255, 0.3)');
    root.style.setProperty('--scrollbar-thumb-active-color', 'rgba(255, 255, 255, 0.4)');
  } else {
    root.style.setProperty('--scrollbar-thumb-color', 'rgba(0, 0, 0, 0.2)');
    root.style.setProperty('--scrollbar-thumb-hover-color', 'rgba(0, 0, 0, 0.3)');
    root.style.setProperty('--scrollbar-thumb-active-color', 'rgba(0, 0, 0, 0.4)');
  }
}

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
 * Shows average coding duration for each hour of the day.
 * @param {Array<Object>} data - Array of data points with hour and seconds
 * @param {number} totalDays - Total number of active days for averaging
 * @param {Object} theme - Theme colors
 */
function renderOverallHourlyChart(data, totalDays, theme) {
  disposeChart('overallHourlyChart');

  const chartDom = document.getElementById('overallHourlyChart');
  const chartTheme = theme.isDark ? 'dark' : 'default';
  chartInstances.overallHourlyChart = echarts.init(chartDom, chartTheme);

  // Create 24 full hours
  const hours = Array.from({length: 24}, (_, i) =>
      `${i.toString().padStart(2, '0')}:00`
  );

  const values = new Array(24).fill(0);

  // Fill in the actual data
  for (const item of data) {
    values[item.hour] = item.seconds / 3600; // Convert to hours
  }

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: 'Average Hourly Coding Duration',
      subtext: `Based on ${totalDays} active days`,
      left: 'center',
      top: 0,
      textStyle: {
        color: theme.foreground
      },
      subtextStyle: {
        color: theme.secondary,
        fontSize: 12
      }
    },
    tooltip: {
      trigger: 'axis',
      formatter: function (params) {
        if (!params || params.length === 0) return '';
        const timeLabel = params[0].axisValue;
        const hours = Math.floor(params[0].value);
        const minutes = Math.round((params[0].value - hours) * 60);
        const seconds = Math.round(((params[0].value - hours) * 60 - minutes) * 60);

        let timeStr = '';
        if (hours > 0) timeStr += `${hours}h `;
        if (minutes > 0) timeStr += `${minutes}m `;
        if (seconds > 0 || timeStr === '') timeStr += `${seconds}s`;

        return `${timeLabel}<br/>Average: ${timeStr}`;
      }
    },
    grid: {
      top: 80,
      left: 50,
      right: 30,
      bottom: 30
    },
    xAxis: {
      type: 'category',
      data: hours,
      axisLabel: {
        color: theme.secondary,
        interval: 1
      },
      axisLine: {
        lineStyle: {
          color: theme.secondary
        }
      }
    },
    yAxis: {
      type: 'value',
      name: 'Average Duration (hours)',
      nameTextStyle: {
        color: theme.secondary
      },
      axisLabel: {
        color: theme.secondary,
        formatter: '{value}h'
      },
      splitLine: {
        lineStyle: {
          color: theme.isDark ? '#333' : '#e0e0e0'
        }
      }
    },
    series: [{
      data: values,
      type: 'bar',
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          {offset: 0, color: '#83bff6'},
          {offset: 1, color: '#188df0'}
        ])
      },
      emphasis: {
        itemStyle: {
          color: '#188df0'
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
