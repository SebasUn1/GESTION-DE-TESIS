import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listarAsesorados } from '../api/tesistrack'
import EstadoBadge from '../components/EstadoBadge'
import { useAuth } from '../auth/AuthContext'
import { Card, Cargando, ErrorMsg, PageHead, Vacio, nombres } from '../components/ui'

/**
 * Asesorías privadas: el acompañamiento personal, uno a uno, fuera de una clase.
 *
 * Lista las tesis que el asesor lleva por su cuenta —las de estudiantes que lo
 * eligieron por su nombre— y qué necesita atención de cada una. El seguimiento de los
 * grupos de una clase no está acá sino en la propia clase (Decisión 24).
 *
 * Responde la pregunta con la que abre la app —¿a quién le presto atención hoy?—
 * sin tener que entrar tesis por tesis. Por eso el backend ordena primero a los
 * que tienen pendientes, y las cifras son de cosas por hacer, no de logros.
 */
export default function AsesoradosPage() {
  const { user } = useAuth()
  const [asesorados, setAsesorados] = useState([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    listarAsesorados()
      .then(setAsesorados)
      .catch((e) => setError(e.message))
      .finally(() => setCargando(false))
  }, [])

  const conSeguimiento = asesorados.filter(
      (a) =>
          a.entregasPorRevisar +
            a.observacionesPendientes +
            a.hitosEnFalta +
            a.tareasVencidas >
          0,
  ).length

  if (cargando) return <Cargando />

  // El menú no ofrece esta pantalla a quien no da asesorías privadas, pero se puede
  // llegar por un enlace: se explica en vez de mostrar una lista vacía sin sentido.
  if (user?.asesoriasPrivadas !== true && asesorados.length === 0) {
    return (
      <>
        <PageHead titulo="Asesorías privadas" descripcion="Acompañamiento personal, uno a uno." />
        <Card>
          <Vacio
            cta={
              <Link className="btn btn--primario" to="/perfil">
                Ir a mi perfil
              </Link>
            }
          >
            No estás dando asesorías privadas. Si querés acompañar a alguien uno a uno, fuera de
            una clase, activalo en tu perfil. Si trabajás con grupos, usá Mis clases.
          </Vacio>
        </Card>
      </>
    )
  }

  return (
    <>
      <PageHead
        titulo="Asesorías privadas"
        descripcion={
          asesorados.length === 0
            ? 'Los estudiantes que acompañás uno a uno, fuera de una clase.'
            : conSeguimiento === 0
              ? 'Todo al día: no hay señales que requieran seguimiento.'
              : `${conSeguimiento} de ${asesorados.length} ${
                  conSeguimiento === 1 ? 'requiere' : 'requieren'
                } seguimiento.`
        }
      />

      {error && <ErrorMsg>{error}</ErrorMsg>}

      {asesorados.length === 0 ? (
        <Card>
          <Vacio>
            Todavía no tenés asesorados privados. Los estudiantes que te elijan por tu nombre al
            crear su tesis aparecen acá. Para tus clases y grupos, usá Mis clases.
          </Vacio>
        </Card>
      ) : (
        <div className="asesorados">
          {asesorados.map((a) => {
            const paraAtender = a.entregasPorRevisar + a.observacionesPendientes
            const fueraDePlazo = a.hitosEnFalta + a.tareasVencidas
            const pendientes = paraAtender + fueraDePlazo
            const progreso =
              a.hitosTotales === 0 ? 0 : Math.round((a.hitosCompletados / a.hitosTotales) * 100)

            return (
              <article
                  key={a.proyectoId}
                  className={`asesorado ${
                    pendientes === 0 ? 'is-al-dia' : fueraDePlazo > 0 ? 'is-en-falta' : ''
                  }`}
              >
                <header className="asesorado__cabecera">
                  <div>
                    <h2>{nombres(a.estudiantes)}</h2>
                    <p className="asesorado__tesis">{a.titulo}</p>
                  </div>
                </header>

                <div className="asesorado__avance">
                  <div className="asesorado__barra" aria-hidden="true">
                    <span style={{ width: `${progreso}%` }} />
                  </div>
                  <span className="lista__meta">
                    {a.hitosCompletados} de {a.hitosTotales} hitos completados
                  </span>
                </div>

                {a.hitoActual ? (
                  <p className="asesorado__hito">
                    <span className="tenue">Va por:</span> <strong>{a.hitoActual.nombre}</strong>{' '}
                    <EstadoBadge estado={a.hitoActual.estado} />
                  </p>
                ) : (
                  <p className="asesorado__hito tenue">
                    {a.hitosTotales === 0
                      ? 'Sin hitos definidos todavía.'
                      : 'Todos los hitos completados.'}
                  </p>
                )}

                {pendientes === 0 ? (
                  <p className="asesorado__ok">✓ Sin alertas de seguimiento.</p>
                ) : (
                    <ul className="asesorado__pendientes" aria-label="Señales de seguimiento">
                      {a.hitosEnFalta > 0 && (
                          <li className="is-en-falta">
                            <Link to="/hitos">
                              <span className="semaforo semaforo--falta" aria-hidden="true">
                                !
                              </span>
                              <span>
                                {a.hitosEnFalta} {a.hitosEnFalta === 1 ? 'hito en falta' : 'hitos en falta'}
                              </span>
                            </Link>
                          </li>
                      )}

                      {a.tareasVencidas > 0 && (
                          <li className="is-vencida">
                            <Link to="/tareas">
                              <span className="semaforo semaforo--falta" aria-hidden="true">
                                !
                              </span>
                              <span>
                                {a.tareasVencidas}{' '}
                                {a.tareasVencidas === 1 ? 'tarea vencida' : 'tareas vencidas'}
                              </span>
                            </Link>
                          </li>
                      )}

                      {a.entregasPorRevisar > 0 && (
                          <li>
                            <Link to="/observaciones">
                              <span className="semaforo semaforo--revisar" aria-hidden="true">
                                R
                              </span>
                              <span>
                                {a.entregasPorRevisar}{' '}
                                {a.entregasPorRevisar === 1 ? 'hito por revisar' : 'hitos por revisar'}
                              </span>
                            </Link>
                          </li>
                      )}

                      {a.observacionesPendientes > 0 && (
                          <li>
                            <Link to="/observaciones">
                              <span className="semaforo semaforo--observado" aria-hidden="true">
                                O
                              </span>
                              <span>
                                {a.observacionesPendientes}{' '}
                                {a.observacionesPendientes === 1
                                    ? 'observación sin resolver'
                                    : 'observaciones sin resolver'}
                              </span>
                            </Link>
                          </li>
                      )}
                    </ul>
                )}
              </article>
            )
          })}
        </div>
      )}
    </>
  )
}
