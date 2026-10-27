import axios from "axios";

// Base URL of the backend API.
// For local development this defaults to the local Spring Boot server.
// In production (Vercel), set the REACT_APP_API_URL environment variable at
// build time to your Render backend URL, e.g. https://resolveit-backend.onrender.com
export const API_URL = process.env.REACT_APP_API_URL || "http://localhost:8080";

const api = axios.create({
  baseURL: API_URL,
});

// Add token to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

export default api;
