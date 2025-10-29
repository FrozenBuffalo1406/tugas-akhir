import React, { useState, useEffect } from 'react';
import { VictoryChart, VictoryArea, VictoryAxis } from 'victory';

// Fungsi buat bikin data EKG dummy yang bergelombang
const generateEcgData = (length, wave) => {
  return Array.from({ length }, (_, i) => ({
    x: i,
    y: Math.sin(i * wave) * 10 + 50 + (Math.random() - 0.5) * 5 // Simulasi gelombang EKG
  }));
};

const HeartRateChart = () => {
  const [data, setData] = useState(generateEcgData(100, 0.1));

  useEffect(() => {
    const interval = setInterval(() => {
      // Perbarui data setiap 500ms buat efek bergelombang
      setData(generateEcgData(100, Math.random() * 0.2 + 0.1));
    }, 500);

    return () => clearInterval(interval);
  }, []);

  return (
    <div>
      <h2 style={{ textAlign: 'center' }}>Detak Jantung</h2>
      <VictoryChart
        theme={{
          area: { style: { data: { fill: '#F25252', fillOpacity: 0.7 } } },
          axis: { style: { tickLabels: { fill: 'white' }, grid: { stroke: 'transparent' } } }
        }}
        domain={{ x: [0, 100], y: [0, 100] }}
        animate={{ duration: 500 }}
      >
        <VictoryArea
          data={data}
          style={{ data: { stroke: '#FF0000', strokeWidth: 2 } }}
          interpolation="natural" // Bikin kurva lebih smooth
        />
        <VictoryAxis style={{ axis: { stroke: 'transparent' } }} />
        <VictoryAxis dependentAxis style={{ axis: { stroke: 'transparent' } }} />
      </VictoryChart>
    </div>
  );
};

export default HeartRateChart;