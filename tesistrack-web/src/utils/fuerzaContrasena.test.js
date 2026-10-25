import { describe, expect, it } from 'vitest'
import evaluarContrasena from './fuerzaContrasena'

describe('evaluarContrasena', () => {
  it('sin texto no evalúa nada', () => {
    expect(evaluarContrasena('')).toBeNull()
  })

  it('debajo de 8 caracteres no cumple el mínimo del backend y dice cuánto falta', () => {
    const r = evaluarContrasena('abc12')
    expect(r.cumpleMinimo).toBe(false)
    expect(r.etiqueta).toBe('Muy débil')
    expect(r.sugerencia).toContain('3 caracteres')
  })

  it('una contraseña del top mundial queda débil aunque le agreguen números', () => {
    const r = evaluarContrasena('password123')
    expect(r.nivel).toBeLessThanOrEqual(1)
    expect(r.avisos.join(' ')).toContain('más usadas')
  })

  it('avisa si la contraseña contiene el propio correo', () => {
    const r = evaluarContrasena('lucia.fernandez2026', { email: 'lucia.fernandez@utec.pe' })
    expect(r.avisos.join(' ')).toContain('correo')
  })

  it('una frase larga llega al máximo aunque no tenga símbolos', () => {
    // El largo pesa más que la variedad: es el criterio del medidor.
    const r = evaluarContrasena('caballo correcto bateria grapa')
    expect(r.etiqueta).toBe('Muy fuerte')
    expect(r.sugerencia).toBeNull()
  })

  it('una secuencia del teclado no pasa de aceptable', () => {
    const r = evaluarContrasena('Qwertyuiop2026')
    expect(r.nivel).toBeLessThanOrEqual(2)
  })
})
