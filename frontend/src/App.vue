<template>
  <div id="app">
    <!-- Bootstrap Navbar -->
    <nav class="navbar navbar-expand-lg navbar-dark bg-primary sticky-top">
      <div class="container-fluid">
        <a class="navbar-brand" href="#">Member Tracker</a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarNav">
          <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="navbarNav">
          <ul class="navbar-nav">
            <li class="nav-item">
              <router-link to="/" class="nav-link" active-class="active">Dashboard</router-link>
            </li>
            <li class="nav-item">
              <router-link to="/members" class="nav-link" active-class="active">Members</router-link>
            </li>
            <li class="nav-item">
              <router-link to="/payments" class="nav-link" active-class="active">Payments</router-link>
            </li>
            <li class="nav-item">
              <router-link to="/communications" class="nav-link" active-class="active">Communications</router-link>
            </li>
          </ul>
        </div>
      </div>
    </nav>

    <!-- Main Content -->
    <div class="container mt-4 mb-5">
      <router-view/>
    </div>

    <!-- Toast Container -->
    <div class="toast-container position-fixed bottom-0 end-0 p-3">
      <div
          id="appToast"
          class="toast"
          role="alert"
          aria-live="assertive"
          aria-atomic="true"
      >
        <div class="toast-header">
          <strong class="me-auto">Notification</strong>
          <button type="button" class="btn-close" data-bs-dismiss="toast" aria-label="Close"></button>
        </div>
        <div class="toast-body">
          <!-- Toast message content will be set dynamically -->
        </div>
      </div>
    </div>
  </div>
</template>

<style>
.toast-container {
  z-index: 11;
}
</style>