function getApiHost(): string {
    return process.env.REACT_APP_CASES_PER_TEST_API_HOST || '';
}

function getMapboxAccessToken(): string {
    return process.env.REACT_APP_MAPBOX_ACCESS_TOKEN || '';
}

export {getApiHost, getMapboxAccessToken};
