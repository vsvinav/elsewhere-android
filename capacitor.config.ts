import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.elsewhere.app',
  appName: 'elsewhere',
  webDir: 'www',
  server: { androidScheme: 'https' },
  android: { backgroundColor: '#efe6d2' },
  plugins: {
    SplashScreen: { launchShowDuration: 0, backgroundColor: '#efe6d2' },
    StatusBar: { style: 'LIGHT', backgroundColor: '#141414' },
  },
}

export default config
