import { Segment, TaxRegime } from './client.enums';

export interface Client {
  clientId: string;
  name: string;
  segment: Segment;
  taxRegime: TaxRegime;
  region: string;
}
