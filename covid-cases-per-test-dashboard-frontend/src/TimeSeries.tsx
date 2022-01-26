import react, { useEffect, useState } from 'react'
import { Container, Row } from 'react-bootstrap';
import { ResponsiveContainer, LineChart, Line, CartesianGrid, XAxis, YAxis, Tooltip } from 'recharts';
import axios from 'axios';
import { getApiHost } from './utils';
import './TimeSeries.css'

interface TimeSeriesProps {
  state: string;
  close: () => void;
}

interface DataPoint {
  date: string;
  value: number;
}

function getHistoricalCasesPerTest(state: string): Promise<DataPoint[]> {
  return axios.get(`${getApiHost()}/historical-cases-per-test?state=${state}`)
    .then((resp) => resp.data).then((dateToValue: Record<string, number>) => {
      let points: DataPoint[] = [];
      for (const [date, value] of Object.entries(dateToValue)) {
        points.push({ date: date, value: value });
      }
      // Sort the data points by dates ascendingly.
      points.sort((point1, point2) => Date.parse(point1.date) - Date.parse(point2.date));
      return points;
    });
}

function TimeSeries(props: TimeSeriesProps) {
  const [dataPoints, setDataPoints] = useState<DataPoint[]>([]);

  useEffect(() => {
    getHistoricalCasesPerTest(props.state)
      .then((fetchedDataPoints) => setDataPoints(fetchedDataPoints));
  }, [props.state]);

  return (
    <Container className="TimeSeries-container">
      <Row className="TimeSeries-close-button-row"><button onClick={props.close}>Close</button></Row>
      <Row><h2>Historical Cases Per Test for {props.state}</h2></Row>
      <Row>
        <ResponsiveContainer width="100%" height={300}>
          <LineChart data={dataPoints}>
            <Line type="linear" dataKey="value" stroke="#8884d8" dot={false} />
            <CartesianGrid stroke="#ccc" />
            <XAxis dataKey="date" angle={-45} tickMargin={30} height={70} />
            <YAxis />
            <Tooltip formatter={(value: number) => [value.toFixed(4), "Cases Per Test"]} />
          </LineChart>
        </ResponsiveContainer>
      </Row>
    </Container >
  );
}

export default TimeSeries;
