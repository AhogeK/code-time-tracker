/**
 * Store all chart instances
 */
const chartInstances = {
  heatmap: null,
  dailyHourHeatmap: null,
  overallHourlyChart: null,
  languageDistributionChart: null,
  projectDistributionChart: null,
  timeOfDayDistributionChart: null
};

/**
 * Renders all charts with the provided data and theme information.
 * @param {string} payload - A JSON string containing theme colors and chart data.
 */
globalThis.renderCharts = function (payload) {
  try {
    const jsonPayload = JSON.parse(payload);
    const theme = jsonPayload.theme;

    applyTheme(theme);

    // Render summary dashboard FIRST for better UX
    if (jsonPayload.summaryData) {
      renderSummary(jsonPayload.summaryData);
    }

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

    // Render language distribution chart
    if (jsonPayload.languageDistribution) {
      renderLanguageDistribution(
          jsonPayload.languageDistribution.data,
          theme
      );
    }

    // Render project distribution chart
    if (jsonPayload.projectDistribution) {
      renderProjectDistribution(
          jsonPayload.projectDistribution.data,
          theme
      );
    }

    // Render time of day distribution chart
    if (jsonPayload.timeOfDayDistribution) {
      renderTimeOfDayDistribution(
          jsonPayload.timeOfDayDistribution.data,
          theme
      );
    }
  } catch (e) {
    console.error("Failed to parse or render chart data:", e);
  }
};

/**
 * Renders summary statistics in the dashboard header with smooth animations.
 * Uses theme-aware colors to ensure visibility in all IDE themes.
 *
 * @param {Object} summaryData - Object containing metric values in seconds
 */
function renderSummary(summaryData) {
  const metrics = ['today', 'dailyAverage', 'thisWeek', 'thisMonth', 'thisYear', 'total'];

  metrics.forEach(metric => {
    const element = document.getElementById(`metric-${metric}`);
    if (element && summaryData[metric] !== undefined) {
      const formattedValue = formatDuration(summaryData[metric]);

      // Apply value with animation
      element.style.opacity = '0';
      element.style.transform = 'scale(0.8)';

      // Use RAF for smooth animation timing
      requestAnimationFrame(() => {
        element.textContent = formattedValue;
        element.style.transition = 'all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1)';
        requestAnimationFrame(() => {
          element.style.opacity = '1';
          element.style.transform = 'scale(1)';
        });
      });
    }
  });
}

/**
 * Applies theme colors to CSS variables for UI elements.
 * Handles both scrollbars and card styling for light/dark modes.
 * @param {Object} theme - Theme colors
 */
function applyTheme(theme) {
  const root = document.documentElement;

  if (theme.isDark) {
    // === Dark Mode ===
    root.style.setProperty('--scrollbar-thumb-color', 'rgba(255, 255, 255, 0.2)');
    root.style.setProperty('--scrollbar-thumb-hover-color', 'rgba(255, 255, 255, 0.3)');
    root.style.setProperty('--scrollbar-thumb-active-color', 'rgba(255, 255, 255, 0.4)');

    root.style.setProperty('--card-bg', 'rgba(255, 255, 255, 0.05)');
    root.style.setProperty('--card-border', 'rgba(255, 255, 255, 0.1)');
    root.style.setProperty('--card-hover-border', 'rgba(255, 255, 255, 0.2)');
    root.style.setProperty('--card-shadow', 'rgba(0, 0, 0, 0.15)');

    root.style.setProperty('--label-opacity', '0.6');
    root.style.setProperty('--metric-label-color', 'var(--text-secondary)');

    root.style.setProperty('--footer-border', 'rgba(128, 128, 128, 0.1)');
    root.style.setProperty('--footer-opacity', '0.5');
    root.style.setProperty('--footer-text-color', 'var(--text-secondary)');

    root.style.setProperty('--btn-bg', 'rgba(128, 128, 128, 0.08)');
    root.style.setProperty('--btn-text', 'var(--text-secondary)');
    root.style.setProperty('--btn-hover-text', '#fff');
  } else {
    // === Light Mode ===
    root.style.setProperty('--scrollbar-thumb-color', 'rgba(0, 0, 0, 0.2)');
    root.style.setProperty('--scrollbar-thumb-hover-color', 'rgba(0, 0, 0, 0.3)');
    root.style.setProperty('--scrollbar-thumb-active-color', 'rgba(0, 0, 0, 0.4)');

    root.style.setProperty('--card-bg', 'rgba(0, 0, 0, 0.02)');
    root.style.setProperty('--card-border', 'rgba(0, 0, 0, 0.12)');
    root.style.setProperty('--card-hover-border', 'rgba(0, 0, 0, 0.25)');
    root.style.setProperty('--card-shadow', 'rgba(0, 0, 0, 0.05)');

    root.style.setProperty('--label-opacity', '1');
    root.style.setProperty('--metric-label-color', '#909090');

    root.style.setProperty('--footer-border', 'rgba(0, 0, 0, 0.1)');

    root.style.setProperty('--footer-opacity', '0.4');

    root.style.setProperty('--footer-text-color', 'var(--text-primary)');
    root.style.setProperty('--btn-text', 'var(--text-primary)');

    root.style.setProperty('--btn-bg', 'rgba(0, 0, 0, 0.05)');
    root.style.setProperty('--btn-hover-text', '#fff');
  }

  // Global text colors
  root.style.setProperty('--text-primary', theme.foreground);
  root.style.setProperty('--text-secondary', theme.secondary);
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
 * Renders the language distribution chart showing coding time by programming language.
 * @param {Array<Object>} data - Array of language usage data with language name and seconds
 * @param {Object} theme - Theme colors
 */
function renderLanguageDistribution(data, theme) {
  disposeChart('languageDistributionChart');

  const chartDom = document.getElementById('languageDistributionChart');
  if (!chartDom) {
    console.warn('Language distribution chart container not found');
    return;
  }

  const chartTheme = theme.isDark ? 'dark' : 'default';
  chartInstances.languageDistributionChart = echarts.init(chartDom, chartTheme);

  // Transform data for chart: convert seconds to hours
  const allData = data.map(item => ({
    name: item.language,
    value: item.seconds / 3600
  })).sort((a, b) => b.value - a.value);

  const totalHours = allData.reduce((sum, item) => sum + item.value, 0);

  // Filter out languages with less than 0.1% usage
  const minPercentage = 0.1;
  const chartData = allData.filter(item => {
    const percentage = (item.value / totalHours) * 100;
    return percentage >= minPercentage;
  });

  const displayedTotal = chartData.reduce((sum, item) => sum + item.value, 0);
  const othersValue = totalHours - displayedTotal;

  const pieData = chartData.map(item => ({
    name: item.name,
    value: item.value
  }));

  if (othersValue > 0) {
    pieData.push({
      name: 'Others',
      value: othersValue
    });
  }

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: 'Language Distribution',
      subtext: `Total: ${totalHours.toFixed(2)} hours`,
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
      trigger: 'item',
      formatter: function (params) {
        const hours = params.value.toFixed(2);
        const percentage = ((params.value / totalHours) * 100).toFixed(2);
        return `${params.name}<br/>Time: ${hours}h (${percentage}%)`;
      }
    },
    // Adjust grid to make chart more compact
    grid: {
      left: '5%',
      right: '5%',
      top: 60,
      bottom: 20,
      containLabel: true
    },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: '5%',  // Closer to edge
      top: 'middle',  // Vertically centered
      textStyle: {
        color: theme.secondary,
        fontSize: 12
      },
      itemWidth: 14,  // Smaller legend icon
      itemHeight: 14,
      itemGap: 8,  // Smaller gap between items
      formatter: function (name) {
        const item = pieData.find(d => d.name === name);
        if (item) {
          const percentage = ((item.value / totalHours) * 100).toFixed(2);
          return `${name} (${percentage}%)`;
        }
        return name;
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],  // Slightly larger
        center: ['40%', '55%'],  // Adjusted position - more centered with legend
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 8,  // Slightly smaller border radius
          borderColor: theme.isDark ? '#333' : '#fff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 18,  // Slightly smaller
            fontWeight: 'bold',
            formatter: function (params) {
              return params.name;
            },
            color: theme.foreground
          }
        },
        labelLine: {
          show: false
        },
        data: pieData
      }
    ]
  };

  chartInstances.languageDistributionChart.setOption(option);
}

/**
 * Renders the project distribution chart showing coding time by project.
 * @param {Array<Object>} data - Array of project usage data with project name and seconds
 * @param {Object} theme - Theme colors
 */
function renderProjectDistribution(data, theme) {
  disposeChart('projectDistributionChart');

  const chartDom = document.getElementById('projectDistributionChart');
  if (!chartDom) {
    console.warn('Project distribution chart container not found');
    return;
  }

  const chartTheme = theme.isDark ? 'dark' : 'default';
  chartInstances.projectDistributionChart = echarts.init(chartDom, chartTheme);

  // Transform data for chart
  const allData = data.map(item => ({
    name: item.project,
    value: item.seconds / 3600
  })).sort((a, b) => b.value - a.value);

  const totalHours = allData.reduce((sum, item) => sum + item.value, 0);

  const minPercentage = 0.1;
  const chartData = allData.filter(item => {
    const percentage = (item.value / totalHours) * 100;
    return percentage >= minPercentage;
  });

  const displayedTotal = chartData.reduce((sum, item) => sum + item.value, 0);
  const othersValue = totalHours - displayedTotal;

  const pieData = chartData.map(item => ({
    name: item.name,
    value: item.value
  }));

  if (othersValue > 0) {
    pieData.push({
      name: 'Others',
      value: othersValue
    });
  }

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: 'Project Distribution',
      subtext: `Total: ${totalHours.toFixed(2)} hours`,
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
      trigger: 'item',
      formatter: function (params) {
        const hours = params.value.toFixed(2);
        const percentage = ((params.value / totalHours) * 100).toFixed(2);
        return `${params.name}<br/>Time: ${hours}h (${percentage}%)`;
      }
    },
    grid: {
      left: '5%',
      right: '5%',
      top: 60,
      bottom: 20,
      containLabel: true
    },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: '5%',
      top: 'middle',
      textStyle: {
        color: theme.secondary,
        fontSize: 12
      },
      itemWidth: 14,
      itemHeight: 14,
      itemGap: 8,
      formatter: function (name) {
        const item = pieData.find(d => d.name === name);
        if (item) {
          const percentage = ((item.value / totalHours) * 100).toFixed(2);
          return `${name} (${percentage}%)`;
        }
        return name;
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['40%', '55%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 8,
          borderColor: theme.isDark ? '#333' : '#fff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 18,
            fontWeight: 'bold',
            formatter: function (params) {
              return params.name;
            },
            color: theme.foreground
          }
        },
        labelLine: {
          show: false
        },
        data: pieData
      }
    ]
  };

  chartInstances.projectDistributionChart.setOption(option);
}

/**
 * Renders the time of day distribution chart showing coding time by time periods.
 * Displays as a horizontal bar chart for better readability.
 * @param {Array<Object>} data - Array of time of day usage data
 * @param {Object} theme - Theme colors
 */
function renderTimeOfDayDistribution(data, theme) {
  disposeChart('timeOfDayDistributionChart');

  const chartDom = document.getElementById('timeOfDayDistributionChart');
  if (!chartDom) {
    console.warn('Time of day distribution chart container not found');
    return;
  }

  const chartTheme = theme.isDark ? 'dark' : 'default';
  chartInstances.timeOfDayDistributionChart = echarts.init(chartDom, chartTheme);

  // Define time period order (from morning to night) and labels with emojis
  const timePeriodOrder = ['Morning', 'Daytime', 'Evening', 'Night'];
  const timePeriodLabels = {
    'Morning': '🌞 Morning (06:00-11:59)',
    'Daytime': '🌆 Daytime (12:00-17:59)',
    'Evening': '🌃 Evening (18:00-23:59)',
    'Night': '🌙 Night (00:00-05:59)'
  };

  // Define colors for each time period (by index order)
  const timePeriodColors = ['#91cc75', '#fac858', '#ee6666', '#5470c6'];

  // Create data map and calculate total
  const dataMap = {};
  let totalSeconds = 0;
  data.forEach(item => {
    const seconds = item.seconds || 0;
    dataMap[item.timeOfDay] = seconds;
    totalSeconds += seconds;
  });

  const totalHours = totalSeconds / 3600;

  // Prepare chart data in correct order
  const chartData = timePeriodOrder.map((period, index) => {
    const seconds = dataMap[period] || 0;
    const hours = seconds / 3600;
    const percentage = totalSeconds > 0 ? (seconds / totalSeconds) * 100 : 0;
    return {
      name: timePeriodLabels[period],
      value: hours,
      percentage: percentage,
      color: timePeriodColors[index]
    };
  });

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: 'Time of Day Distribution',
      subtext: `Total: ${totalHours.toFixed(2)} hours`,
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
      axisPointer: {
        type: 'shadow'
      },
      formatter: function (params) {
        if (!params || params.length === 0) return '';
        const data = params[0];
        const hours = data.value.toFixed(2);
        const percentage = chartData[data.dataIndex].percentage.toFixed(2);
        return `${data.name}<br/>Time: ${hours}h (${percentage}%)`;
      }
    },
    grid: {
      left: '20%',
      right: '10%',
      top: 80,
      bottom: 30,
      containLabel: true
    },
    xAxis: {
      type: 'value',
      name: 'Hours',
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
    yAxis: {
      type: 'category',
      data: chartData.map(item => item.name),
      inverse: true,  // 🔑 Add this line to reverse Y axis order
      axisLabel: {
        color: theme.secondary,
        fontSize: 12
      },
      axisLine: {
        lineStyle: {
          color: theme.secondary
        }
      }
    },
    series: [
      {
        type: 'bar',
        data: chartData.map(item => ({
          value: item.value,
          itemStyle: {
            color: item.color
          }
        })),
        barWidth: '60%',
        label: {
          show: true,
          position: 'right',
          formatter: function (params) {
            const percentage = chartData[params.dataIndex].percentage;
            return `${params.value.toFixed(2)}h (${percentage.toFixed(2)}%)`;
          },
          color: theme.foreground,
          fontSize: 11
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  };

  chartInstances.timeOfDayDistributionChart.setOption(option);
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
 * Formats duration in seconds to human-readable format.
 * Modified to use Hours as the largest unit (e.g., "26h 30m" instead of "1d 2h 30m").
 *
 * Display rules:
 * - Shows hours, minutes, and seconds
 * - Always shows at least the minute component unless it's just seconds
 *
 * Examples:
 * - 37 seconds → "37s"
 * - 3097 seconds → "51m 37s"
 * - 7337 seconds → "2h 2m 17s"
 * - 90061 seconds → "25h 1m 1s"
 *
 * @param {number} seconds - Duration in seconds
 * @returns {string} Formatted duration string
 */
function formatDuration(seconds) {
  if (seconds === 0) return '0s';

  // Calculate total hours directly (do not extract days)
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = Math.floor(seconds % 60);

  const parts = [];

  if (hours > 0) {
    parts.push(`${hours}h`);
  }
  if (minutes > 0) {
    parts.push(`${minutes}m`);
  }
  if (secs > 0) {
    parts.push(`${secs}s`);
  }

  // Fallback: if all components are zero (shouldn't happen), return "0s"
  return parts.length > 0 ? parts.join(' ') : '0s';
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
