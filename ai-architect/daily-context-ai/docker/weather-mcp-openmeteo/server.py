import httpx
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("weather-openmeteo", host="0.0.0.0", port=8080)

GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
FORECAST_URL = "https://api.open-meteo.com/v1/forecast"


async def geocode(location: str) -> dict | None:
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(GEOCODING_URL, params={"name": location, "count": 1})
        resp.raise_for_status()
        data = resp.json()
        results = data.get("results")
        if not results:
            return None
        return results[0]


@mcp.tool()
async def openmeteo_get_current_weather(location: str) -> str:
    """Get current weather for a location using Open-Meteo (no API key required)."""
    geo = await geocode(location)
    if not geo:
        return f"Location '{location}' not found."

    lat, lon, name = geo["latitude"], geo["longitude"], geo.get("name", location)
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(FORECAST_URL, params={
            "latitude": lat,
            "longitude": lon,
            "current": "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,apparent_temperature",
            "timezone": "auto",
        })
        resp.raise_for_status()
        data = resp.json()

    current = data.get("current", {})
    units = data.get("current_units", {})
    return (
        f"Current weather in {name}:\n"
        f"  Temperature: {current.get('temperature_2m')}{units.get('temperature_2m', '°C')}\n"
        f"  Feels like: {current.get('apparent_temperature')}{units.get('apparent_temperature', '°C')}\n"
        f"  Humidity: {current.get('relative_humidity_2m')}{units.get('relative_humidity_2m', '%')}\n"
        f"  Wind speed: {current.get('wind_speed_10m')}{units.get('wind_speed_10m', 'km/h')}\n"
        f"  Weather code: {current.get('weather_code')}"
    )


@mcp.tool()
async def openmeteo_get_weather_forecast(location: str, days: int = 3) -> str:
    """Get weather forecast for a location using Open-Meteo (no API key required)."""
    days = max(1, min(days, 16))
    geo = await geocode(location)
    if not geo:
        return f"Location '{location}' not found."

    lat, lon, name = geo["latitude"], geo["longitude"], geo.get("name", location)
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(FORECAST_URL, params={
            "latitude": lat,
            "longitude": lon,
            "daily": "temperature_2m_max,temperature_2m_min,weather_code",
            "forecast_days": days,
            "timezone": "auto",
        })
        resp.raise_for_status()
        data = resp.json()

    daily = data.get("daily", {})
    units = data.get("daily_units", {})
    dates = daily.get("time", [])
    max_temps = daily.get("temperature_2m_max", [])
    min_temps = daily.get("temperature_2m_min", [])
    codes = daily.get("weather_code", [])

    lines = [f"{days}-day forecast for {name}:"]
    for i, date in enumerate(dates):
        lines.append(
            f"  {date}: {min_temps[i]}-{max_temps[i]}{units.get('temperature_2m_max', '°C')}, "
            f"code {codes[i]}"
        )
    return "\n".join(lines)


mcp.run(transport="streamable-http")
