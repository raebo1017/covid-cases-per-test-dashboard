import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import './Choropleth.css'
import 'leaflet/dist/leaflet.css';
import { scaleLinear } from 'd3-scale';
import { extent, range } from 'd3-array';
import geoData from './data/leaflet-example.json';
import { PathOptions, Layer, LeafletMouseEvent, Map } from 'leaflet';
import L from 'leaflet';
import { FeatureCollection, Feature, GeometryObject, GeoJsonProperties } from 'geojson';
import { MapContainer, GeoJSON, TileLayer } from 'react-leaflet';
import { Container } from 'react-bootstrap';
import TimeSeries from './TimeSeries';
import { getApiHost, getMapboxAccessToken } from './utils';

function getLatestCasesPerTestForAllStates(): Promise<Record<string, number>> {
  return axios.get(`${getApiHost()}/latest-cases-per-test`)
    .then((resp) => resp.data);
}

const Choropleth = () => {
  const [stateToCasesPerTest, setStateToCasesPerTest] = useState<Record<string, number>>({});
  const [isDataLoading, setIsDataLoading] = useState(true);
  const [selectedState, setSelectedState] = useState("");

  // These states or values need to be accessed in callbacks.
  // Wrapping them in refs allow callbacks to access the latest values.
  const stateToCasesPerTestRef = useRef(stateToCasesPerTest);
  stateToCasesPerTestRef.current = stateToCasesPerTest;
  const selectedStateRef = useRef(selectedState);
  selectedStateRef.current = selectedState;
  const tooltipRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<Map | null>(null);

  const casesPerTestMaxMin = extent(Object.values(stateToCasesPerTest));
  const colorScale = scaleLinear(['#BFBFFF', '#0000FF'])
    .domain(casesPerTestMaxMin[0] !== undefined && casesPerTestMaxMin[1] !== undefined ? casesPerTestMaxMin : [0, 1]);

  useEffect(() => {
    getLatestCasesPerTestForAllStates().then((fetchedStateToCasesPerTest) => {
      setStateToCasesPerTest(fetchedStateToCasesPerTest);
      setIsDataLoading(false);

      if (fetchedStateToCasesPerTest && mapRef.current) {
        // Add color scale.
        const [max, min] = extent(Object.values(fetchedStateToCasesPerTest));
        if ((min !== undefined) && (max !== undefined)) {
          const LegendControl = L.Control.extend({
            onAdd: () => {
              const div = L.DomUtil.create('div', 'legend');
              div.innerHTML += 'Scale<br>'
              for (const casePerTest of range(min, max, (max - min) / 10)) {
                div.innerHTML += `<i style="background: ${colorScale(casePerTest)}">${casePerTest.toFixed(4)}</i><br>`;
              }
              return div;
            }
          });

          mapRef.current.addControl(new LegendControl({ position: 'bottomleft' }));
        }

        // Add tooltip for showing the cases per test value when hovering over a state.
        const TooltipControl = L.Control.extend({
          onAdd: () => {
            const div = L.DomUtil.create('div', 'tooltip');
            tooltipRef.current = div;
            return div;
          }
        });
        mapRef.current.addControl(new TooltipControl({ position: 'topright' }));
      }
    });
  }, []); // Set dependency array to an empty array to load data only once.

  const getStateStylingOptions = (feature?: Feature<GeometryObject, GeoJsonProperties>): PathOptions => {
    const options = {
      weight: 2,
      opacity: 1,
      color: 'white',
      dashArray: '3',
      fillOpacity: 0.7
    };
    if (!feature || !feature.properties) {
      return options;
    }
    if (selectedStateRef.current && feature.properties.name === selectedStateRef.current) {
      return {
        weight: 3,
        opacity: 1,
        color: 'yellow',
        dashArray: '',
        fillOpacity: 0.7
      };
    }
    return { fillColor: colorScale(stateToCasesPerTestRef.current[feature.properties.name]), ...options };
  }

  const onMouseOver = (event: LeafletMouseEvent) => {
    if (selectedStateRef.current && (event as any).target.feature.properties.name === selectedStateRef.current) {
      return;
    }
    const stateLayer = event.target;
    stateLayer.setStyle({
      weight: 3,
      color: 'black',
      dashArray: '',
    });
    stateLayer.bringToFront();

    if (tooltipRef.current) {
      // Show tooltip for the state.
      const state = event.target.feature.properties.name;
      tooltipRef.current.innerHTML = `${state}<br>${stateToCasesPerTestRef.current[state].toFixed(4)}`;
      tooltipRef.current.style.visibility = 'visible';
    }
  }

  const onMouseOut = (event: LeafletMouseEvent) => {
    if (selectedStateRef.current && (event as any).target.feature.properties.name === selectedStateRef.current) {
      return;
    }

    event.target.setStyle(getStateStylingOptions((event as any).feature));

    if (tooltipRef.current) {
      // Hide tooltip.
      tooltipRef.current.style.visibility = 'hidden';
    }
  }

  const onClick = (event: LeafletMouseEvent) => {
    const stateLayer = event.target;
    stateLayer.setStyle({
      weight: 3,
      color: 'yellow',
      dashArray: '',
    });
    stateLayer.bringToFront();

    setSelectedState(event.target.feature.properties.name);
  }

  const onEachState = (feature: Feature<GeometryObject, GeoJsonProperties>, stateLayer: Layer) => {
    stateLayer.on({
      mouseover: onMouseOver,
      mouseout: onMouseOut,
      click: onClick
    });
  }

  const clearSelectedState = () => {
    setSelectedState("");
  }

  const setMapRef = (map: Map) => {
    mapRef.current = map;
  }

  return (
    <Container>
      {isDataLoading && <p>LOADING...</p>}
      {selectedState && <TimeSeries state={selectedState} close={clearSelectedState} />}
      <MapContainer center={[40.930626, -99.168823]}
        zoom={4} scrollWheelZoom={true} whenCreated={setMapRef}>
        <TileLayer
          url='https://api.mapbox.com/styles/v1/mapbox/light-v10/tiles/{z}/{x}/{y}?access_token={accessToken}'
          accessToken={getMapboxAccessToken()}
        />
        <GeoJSON data={geoData as FeatureCollection} style={getStateStylingOptions} onEachFeature={onEachState} />
      </MapContainer>
    </Container>
  )
}

export default Choropleth;