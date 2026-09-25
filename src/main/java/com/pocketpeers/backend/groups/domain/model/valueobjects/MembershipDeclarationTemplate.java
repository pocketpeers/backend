package com.pocketpeers.backend.groups.domain.model.valueobjects;

/**
 * Texto de la declaracion jurada que se firma al entrar a un grupo.
 *
 * <p>La version forma parte de lo que se sella. Por eso el texto de una version
 * no se toca nunca: si hay que cambiar una sola palabra, se crea una version
 * nueva y la anterior se deja tal cual. Si se editara en el sitio, las firmas
 * ya guardadas seguirian verificando contra su propio texto —que viaja con
 * ellas— pero dejaria de ser cierto que la version "1.0" significa una sola
 * cosa.</p>
 *
 * <p>El texto se arma en el servidor y el cliente solo lo muestra. Si lo armara
 * la aplicacion, lo que se sella seria lo que el cliente dice haber mostrado, no
 * lo que el usuario leyo.</p>
 */
public final class MembershipDeclarationTemplate {

    public static final String CURRENT_VERSION = "1.0";

    private static final String NO_DOCUMENT = "documento no registrado";

    private MembershipDeclarationTemplate() {
    }

    public static String render(String fullName, String documentType, String documentNumber, String groupName) {
        var document = documentNumber == null || documentNumber.isBlank()
                ? NO_DOCUMENT
                : "%s N.° %s".formatted(documentType, documentNumber);

        return """
                DECLARACIÓN JURADA DE VERACIDAD Y COMPROMISO DE PARTICIPACIÓN EN GRUPO
                PocketPeers - Versión %s

                Yo, %s, identificado(a) con %s, al incorporarme al grupo "%s", declaro bajo juramento lo siguiente:

                1. VERACIDAD DE LOS MOVIMIENTOS
                Todos los movimientos (aportes, pagos y gastos) que registre en este grupo corresponden a operaciones reales efectivamente realizadas, y los comprobantes o capturas que adjunte (Yape, Plin, transferencias u otros) son auténticos y no han sido alterados.

                2. CONFIRMACIÓN ENTRE MIEMBROS
                Me comprometo a confirmar únicamente los movimientos que realmente haya recibido o realizado.

                3. REGISTRO PROTEGIDO
                Conozco y acepto que esta declaración, firmada por mí, y mis movimientos quedan registrados con fecha y hora, protegidos con un código de verificación que permite detectar cualquier alteración posterior.

                4. HISTORIAL DE PARTICIPACIÓN
                Autorizo que los miembros de los grupos a los que pertenezca puedan ver un resumen de mi historial de participación (grupos en los que participé y movimientos confirmados), sin el detalle de los movimientos de otros grupos.

                5. ALCANCE DEL SCORE
                Entiendo que el score de PocketPeers es un indicador de uso exclusivo dentro de la aplicación, que refleja mi progreso en hábitos financieros. No constituye una calificación crediticia, no se comparte con bancos ni entidades financieras y no puede usarse como prueba de solvencia ante terceros.

                6. CONSECUENCIAS DEL INCUMPLIMIENTO
                Si se detectan movimientos falsos o comprobantes alterados: (a) el incidente quedará registrado en mi historial; (b) PocketPeers podrá retirarme del grupo o de la plataforma; (c) lo anterior no excluye las acciones legales que correspondan a los miembros afectados.

                7. TRATAMIENTO DE DATOS PERSONALES
                Autorizo el tratamiento de mi nombre, documento de identidad, firma y datos de actividad para las finalidades descritas, conforme a la Ley N.° 29733, Ley de Protección de Datos Personales, y su reglamento.

                8. ACEPTACIÓN
                Declaro haber leído y comprendido este documento. La firma que dibujo a continuación expresa mi aceptación y tiene el mismo valor que mi firma manuscrita, conforme a la Ley N.° 27269, Ley de Firmas y Certificados Digitales.
                """.formatted(CURRENT_VERSION, fullName, document, groupName);
    }
}
