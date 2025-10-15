# Member Tracker Frontend

A modern Vue 3 application for managing church members, payments, and communications.

## Tech Stack

- **Vue 3** - Progressive JavaScript Framework
- **Vue Router 4** - Official router for Vue.js
- **Axios** - Promise-based HTTP client
- **Bootstrap 5** - CSS Framework
- **Moment.js** - Date manipulation library

## Project Structure

```
frontend/
├── src/
│   ├── assets/          # Static assets (images, styles)
│   ├── components/      # Reusable Vue components
│   ├── composables/     # Vue composition API composables
│   ├── constants/       # Application constants
│   ├── plugin/          # Vue plugins
│   ├── router/          # Vue Router configuration
│   ├── services/        # API services
│   ├── utils/           # Utility functions
│   ├── views/           # Page components
│   ├── App.vue          # Root component
│   └── main.js          # Application entry point
├── .editorconfig        # Editor configuration
├── .eslintrc.js         # ESLint configuration
├── .prettierrc.json     # Prettier configuration
├── .gitignore           # Git ignore rules
├── jsconfig.json        # JavaScript configuration
├── package.json         # Project dependencies
└── vue.config.js        # Vue CLI configuration
```

## Prerequisites

- Node.js (v18 or higher)
- npm or yarn

## Installation

1. Install dependencies:
```bash
npm install
```

2. Copy the environment file:
```bash
cp .env.example .env.development
```

3. Update environment variables as needed

## Development

Start the development server:
```bash
npm run dev
# or
npm run serve
```

The application will be available at `http://localhost:8081`

## Building for Production

Build the application:
```bash
npm run build
# or
npm run build:prod
```

The build artifacts will be stored in the `dist/` directory.

## Code Quality

### Linting

Run ESLint:
```bash
npm run lint
```

Auto-fix linting issues:
```bash
npm run lint:fix
```

### Formatting

Format code with Prettier:
```bash
npm run format
```

Check formatting:
```bash
npm run format:check
```

## Available Scripts

- `npm run dev` - Start development server
- `npm run serve` - Start development server (alias)
- `npm run build` - Build for production
- `npm run build:prod` - Build for production with production mode
- `npm run lint` - Run ESLint
- `npm run lint:fix` - Fix ESLint issues
- `npm run format` - Format code with Prettier
- `npm run format:check` - Check code formatting

## Features

- Dashboard with overview statistics
- Member management
- Payment tracking
- Communication tools
- Responsive design with Bootstrap 5
- Toast notifications
- PDF export functionality

## Development Best Practices

1. **Component Organization**
   - Use PascalCase for component names
   - Keep components small and focused
   - Extract reusable logic into composables

2. **Code Style**
   - Follow the configured ESLint rules
   - Use Prettier for consistent formatting
   - Write meaningful commit messages

3. **State Management**
   - Use composables for shared state
   - Keep API calls in services
   - Use constants for magic values

4. **Routing**
   - Use named routes
   - Lazy-load route components when possible

## API Integration

The frontend communicates with a Spring Boot backend running on `http://localhost:8080`. API requests are proxied through the dev server configuration.

## Environment Variables

- `VITE_API_BASE_URL` - Base URL for API requests
- `NODE_ENV` - Environment mode (development/production)

## Browser Support

- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)

## Contributing

1. Follow the code style guidelines
2. Run linting and formatting before committing
3. Write clear commit messages
4. Test your changes thoroughly

## License

MIT
