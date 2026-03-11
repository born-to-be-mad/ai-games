import os
import httpx
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("weather-openweathermap", host="0.0.0.0", port=8080)

BASE_URL = "https://api.openweathermap.org"
API_KEY = os.environ.get("OPENWEATHERMAP_KEY", "")


async def geocode(location: str) -> dict | None:
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(f"{BASE_URL}/geo/1.0/direct", params={
            "q": location,
            "limit": 1,
            "appid": API_KEY,
        })
        if resp.status_code == 401:
            return {"error": "Invalid OpenWeatherMap key."}
        resp.raise_for_status()
        data = resp.json()
        if not data:
            return None
        return data[0]


@mcp.tool()
async def get_current_weather(location: str) -> str:
    """Get current weather for a location using OpenWeatherMap."""
    if not API_KEY:
        return "OpenWeatherMap key not configured. Set OPENWEATHERMAP_KEY environment variable."

    geo = await geocode(location)
    if geo is None:
        return f"Location '{location}' not found."
    if "error" in geo:
        return geo["error"]

    lat, lon, name, country = geo["lat"], geo["lon"], geo["name"], geo.get("country", "")
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(f"{BASE_URL}/data/2.5/weather", params={
            "lat": lat,
            "lon": lon,
            "appid": API_KEY,
            "units": "metric",
        })
        if resp.status_code == 401:
            return "Invalid OpenWeatherMap key."
        resp.raise_for_status()
        data = resp.json()

    weather = data["weather"][0]["description"]
    main = data["main"]
    wind = data["wind"]
    return (
        f"Current weather in {name}, {country}:\n"
        f"  Condition: {weather}\n"
        f"  Temperature: {main['temp']}°C (feels like {main['feels_like']}°C)\n"
        f"  Min/Max: {main['temp_min']}/{main['temp_max']}°C\n"
        f"  Humidity: {main['humidity']}%\n"
        f"  Wind: {wind.get('speed')} m/s"
    )


@mcp.tool()
async def get_weather_forecast(location: str, days: int = 3) -> str:
    """Get weather forecast for a location using OpenWeatherMap (5-day/3h, sampled daily)."""
    if not API_KEY:
        return "OpenWeatherMap key not configured. Set OPENWEATHERMAP_KEY environment variable."

    days = max(1, min(days, 5))
    geo = await geocode(location)
    if geo is None:
        return f"Location '{location}' not found."
    if "error" in geo:
        return geo["error"]

    lat, lon, name, country = geo["lat"], geo["lon"], geo["name"], geo.get("country", "")
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(f"{BASE_URL}/data/2.5/forecast", params={
            "lat": lat,
            "lon": lon,
            "appid": API_KEY,
            "units": "metric",
            "cnt": days * 8,  # 8 entries per day (every 3h)
        })
        if resp.status_code == 401:
            return "Invalid OpenWeatherMap key."
        resp.raise_for_status()
        data = resp.json()

    # Sample first entry per day
    seen_dates = {}
    for entry in data["list"]:
        date = entry["dt_txt"].split(" ")[0]
        if date not in seen_dates:
            seen_dates[date] = entry

    lines = [f"{days}-day forecast for {name}, {country}:"]
    for date, entry in list(seen_dates.items())[:days]:
        main = entry["main"]
        weather = entry["weather"][0]["description"]
        lines.append(
            f"  {date}: {main['temp_min']}-{main['temp_max']}°C, {weather}"
        )
    return "\n".join(lines)


mcp.run(transport="streamable-http")
