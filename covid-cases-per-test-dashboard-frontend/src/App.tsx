import React from 'react';
import './App.css';
import Choropleth from './Choropleth';
import { Container, Row } from 'react-bootstrap';

function App() {
  return (
    <Container fluid className="App">
      <Row><h1>US State COVID-19 Cases Per Test Dashboard</h1></Row>
      <Row>
        <p>
          Cases per test is defined as number of cases divided by number of tests.
          Data is from the <a href="https://github.com/CSSEGISandData/COVID-19/tree/master/csse_covid_19_data/csse_covid_19_daily_reports_us">JHU GitHub repository</a>.
        </p>
        <p>
          The choropleth map uses the latest daily data. Hovering over a state shows
          its latest cases per test value. Clicking on a state displays a time series
          showing its historical cases per test values.
        </p>
      </Row>
      <Row><Choropleth /></Row>
    </Container>
  );
}

export default App;
