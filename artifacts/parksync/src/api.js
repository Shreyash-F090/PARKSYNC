const API_ROOT = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '');
const TOKEN_KEY = 'parksync.session';
export const session = {
  get token() { return sessionStorage.getItem(TOKEN_KEY); },
  set(token) { if (token) sessionStorage.setItem(TOKEN_KEY, token); },
  clear() { sessionStorage.removeItem(TOKEN_KEY); }
};

async function request(path, { method = 'GET', body, query, auth = true } = {}) {
  const url = new URL(`${API_ROOT}${path}`, window.location.origin);
  if (query) Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
  });
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && session.token) headers.Authorization = `Bearer ${session.token}`;
  let response;
  try {
    response = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new Error('Could not reach PARKSYNC. Check your connection and try again.');
  }
  if (response.status === 401 && auth) {
    session.clear();
    window.dispatchEvent(new CustomEvent('parksync:unauthorized'));
    throw new Error('Your session has expired. Please sign in again.');
  }
  if (!response.ok) {
    let message = `Request failed (${response.status}).`;
    try {
      const data = await response.json();
      message = data.message || data.error || message;
    } catch {}
    throw new Error(message);
  }
  if (response.status === 204) return null;
  const contentType = response.headers.get('content-type') || '';
  return contentType.includes('application/json') ? response.json() : null;
}

const enc = value => encodeURIComponent(value);
export const api = {
  get: (path, query) => request(path, { query }),
  post: (path, body) => request(path, { method: 'POST', body }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  patch: (path, body) => request(path, { method: 'PATCH', body }),
  delete: path => request(path, { method: 'DELETE' }),
  login: body => request('/v1/auth/login', { method: 'POST', body, auth: false }),
  register: body => request('/v1/auth/register', { method: 'POST', body, auth: false }),
  logout: () => request('/v1/auth/logout', { method: 'POST' }),
  me: () => request('/v1/auth/me'),
  profile: body => request('/v1/profile', { method: 'PATCH', body }),
  password: body => request('/v1/profile/password', { method: 'PATCH', body }),
  dashboard: () => request('/v1/dashboard'),
  vehicles: () => request('/v1/vehicles'),
  createVehicle: body => request('/v1/vehicles', { method: 'POST', body }),
  updateVehicle: (id, body) => request(`/v1/vehicles/${enc(id)}`, { method: 'PUT', body }),
  deleteVehicle: id => request(`/v1/vehicles/${enc(id)}`, { method: 'DELETE' }),
  locations: query => request('/v1/locations', { query, auth: false }),
  location: id => request(`/v1/locations/${enc(id)}`, { auth: false }),
  slots: id => request(`/v1/locations/${enc(id)}/slots`, { auth: false }),
  quote: body => request('/v1/bookings/quote', { method: 'POST', body }),
  bookings: query => request('/v1/bookings', { query }),
  booking: id => request(`/v1/bookings/${enc(id)}`),
  createBooking: body => request('/v1/bookings', { method: 'POST', body }),
  cancelBooking: id => request(`/v1/bookings/${enc(id)}/cancel`, { method: 'POST' }),
  payments: () => request('/v1/payments'),
  demoPayment: body => request('/v1/payments/demo', { method: 'POST', body }),
  notifications: () => request('/v1/notifications'),
  readNotification: id => request(`/v1/notifications/${enc(id)}/read`, { method: 'POST' }),
  support: () => request('/v1/support'),
  createTicket: body => request('/v1/support', { method: 'POST', body }),
  adminOverview: () => request('/v1/admin/overview'),
  adminUsers: query => request('/v1/admin/users', { query }),
  setUserActive: (id, active) => request(`/v1/admin/users/${enc(id)}/active`, { method: 'PATCH', body: { active } }),
  adminLocations: () => request('/v1/admin/locations'),
  adminSlots: id => request(`/v1/admin/locations/${enc(id)}/slots`),
  setLocationActive: (id, active) => request(`/v1/admin/locations/${enc(id)}/active`, { method: 'PATCH', body: { active } }),
  createLocation: body => request('/v1/admin/locations', { method: 'POST', body }),
  updateLocation: (id, body) => request(`/v1/admin/locations/${enc(id)}`, { method: 'PUT', body }),
  deactivateLocation: id => request(`/v1/admin/locations/${enc(id)}`, { method: 'DELETE' }),
  createSlot: (id, body) => request(`/v1/admin/locations/${enc(id)}/slots`, { method: 'POST', body }),
  updateSlot: (id, body) => request(`/v1/admin/slots/${enc(id)}`, { method: 'PUT', body }),
  adminBookings: query => request('/v1/admin/bookings', { query }),
  entry: id => request(`/v1/admin/bookings/${enc(id)}/entry`, { method: 'POST' }),
  exit: id => request(`/v1/admin/bookings/${enc(id)}/exit`, { method: 'POST' }),
  adminPayments: query => request('/v1/admin/payments', { query }),
  reports: query => request('/v1/admin/reports', { query }),
  adminSupport: () => request('/v1/admin/support'),
  reply: (id, body) => request(`/v1/admin/support/${enc(id)}/reply`, { method: 'POST', body })
};