import { createConfig, http } from 'wagmi'
import { anvil, sepolia } from 'wagmi/chains'

// Anvil (31337) for local development, Sepolia for the demo (RF-UI-02)
export const config = createConfig({
  chains: [anvil, sepolia],
  transports: {
    [anvil.id]: http(),
    [sepolia.id]: http(),
  },
})

declare module 'wagmi' {
  interface Register {
    config: typeof config
  }
}
